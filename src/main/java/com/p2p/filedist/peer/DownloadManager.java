package com.p2p.filedist.peer;

import com.p2p.filedist.integrity.IntegrityManager;
import com.p2p.filedist.model.Bitfield;
import com.p2p.filedist.model.FileMetadata;
import com.p2p.filedist.model.PeerInfo;
import com.p2p.filedist.model.PieceInfo;
import com.p2p.filedist.net.NioClient;
import com.p2p.filedist.protocol.Message;
import com.p2p.filedist.protocol.MessageCodec;
import com.p2p.filedist.protocol.MessageType;
import com.p2p.filedist.storage.StorageManager;
import com.p2p.filedist.tracker.TrackerClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class DownloadManager {
    private static final Logger logger = Logger.getLogger(DownloadManager.class.getName());

    private final String peerId;
    private final int listeningPort;
    private final FileMetadata metadata;
    private final StorageManager storageManager;
    private final TrackerClient trackerClient;
    private final PieceScheduler scheduler;
    private final ExecutorService downloadPool;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong downloadedBytes = new AtomicLong(0);

    @FunctionalInterface
    public interface DownloadProgressListener {
        void onProgress(int verifiedPieces, int totalPieces, long bytesDownloaded);
        default void onComplete(Path savedPath, String fileHash) {}
        default void onError(String message, Throwable cause) {}
    }

    public DownloadManager(String peerId, int listeningPort, FileMetadata metadata,
                           StorageManager storageManager, TrackerClient trackerClient,
                           int concurrency) {
        this.peerId = peerId;
        this.listeningPort = listeningPort;
        this.metadata = metadata;
        this.storageManager = storageManager;
        this.trackerClient = trackerClient;
        Bitfield initialBitfield = storageManager.getBitfield(metadata.getFileId());
        this.scheduler = new PieceScheduler(metadata, initialBitfield);
        this.downloadPool = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r, "DownloadWorker-" + metadata.getFileId().substring(0, 4));
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<Path> startDownload(Path destinationPath, DownloadProgressListener listener) {
        CompletableFuture<Path> future = new CompletableFuture<>();
        if (!running.compareAndSet(false, true)) {
            future.completeExceptionally(new IllegalStateException("Download already running"));
            return future;
        }

        Thread masterThread = new Thread(() -> {
            try {
                // Initialize storage preallocation
                storageManager.initDownload(metadata, destinationPath);

                // Initial announcement to tracker to obtain peers
                List<PeerInfo> peers = trackerClient.announce(peerId, metadata.getFileId(),
                        listeningPort, "STARTED", storageManager.getBitfield(metadata.getFileId()).toByteArray());

                logger.info("Found " + peers.size() + " peers for file " + metadata.getFileName());

                // Download loop
                while (running.get() && !scheduler.isAllVerified()) {
                    if (peers.isEmpty()) {
                        // Re-query tracker
                        Thread.sleep(1000);
                        peers = trackerClient.announce(peerId, metadata.getFileId(),
                                listeningPort, "RUNNING", storageManager.getBitfield(metadata.getFileId()).toByteArray());
                        if (peers.isEmpty()) {
                            continue;
                        }
                    }

                    CountDownLatch batchLatch = new CountDownLatch(peers.size());
                    for (PeerInfo peer : peers) {
                        downloadPool.submit(() -> {
                            try {
                                downloadFromPeer(peer, listener);
                            } catch (Exception e) {
                                logger.fine("Error downloading from " + peer + ": " + e.getMessage());
                            } finally {
                                batchLatch.countDown();
                            }
                        });
                    }

                    batchLatch.await(3, TimeUnit.SECONDS);

                    if (listener != null) {
                        listener.onProgress(scheduler.getCompletedPieceCount(), metadata.getTotalPieces(), downloadedBytes.get());
                    }
                }

                if (scheduler.isAllVerified()) {
                    // Re-verify entire file
                    boolean verified = IntegrityManager.verifyFile(destinationPath, metadata.getFileHash());
                    if (!verified) {
                        throw new IOException("Whole file checksum mismatch for " + destinationPath);
                    }

                    // Announce completion to tracker
                    trackerClient.announce(peerId, metadata.getFileId(), listeningPort, "COMPLETED",
                            storageManager.getBitfield(metadata.getFileId()).toByteArray());

                    logger.info("Download of " + metadata.getFileName() + " successfully completed and verified!");
                    if (listener != null) {
                        listener.onComplete(destinationPath, metadata.getFileHash());
                    }
                    future.complete(destinationPath);
                } else if (!running.get()) {
                    future.completeExceptionally(new CancellationException("Download cancelled"));
                }
            } catch (Exception e) {
                logger.severe("Download failed: " + e.getMessage());
                if (listener != null) {
                    listener.onError(e.getMessage(), e);
                }
                future.completeExceptionally(e);
            } finally {
                stop();
            }
        }, "DownloadMaster-" + metadata.getFileId());

        masterThread.setDaemon(true);
        masterThread.start();
        return future;
    }

    private void downloadFromPeer(PeerInfo peer, DownloadProgressListener listener) {
        Bitfield peerBitfield = Bitfield.fromByteArray(peer.getBitfieldBytes(), metadata.getTotalPieces());

        while (running.get()) {
            Integer pieceIndex = scheduler.selectNextPiece(peerBitfield);
            if (pieceIndex == null) {
                break; // No more pieces needed from this peer
            }

            scheduler.markDownloading(pieceIndex);
            PieceInfo pieceInfo = metadata.getPieceInfo(pieceIndex);

            try {
                Message request = MessageCodec.createRequestPiece(metadata.getFileId(), pieceIndex);
                Message response = NioClient.sendAndReceive(peer.getIpAddress(), peer.getPort(), request, 5000);

                if (response.getType() == MessageType.PIECE_DATA) {
                    MessageCodec.PieceDataPayload dataPayload = MessageCodec.decodePieceData(response.getPayload());

                    // Integrity verification
                    if (IntegrityManager.verifyPiece(dataPayload.data(), pieceInfo.getExpectedSha256())) {
                        storageManager.writePiece(metadata.getFileId(), pieceIndex, dataPayload.data());
                        scheduler.markVerified(pieceIndex);
                        downloadedBytes.addAndGet(dataPayload.data().length);

                        if (listener != null) {
                            listener.onProgress(scheduler.getCompletedPieceCount(), metadata.getTotalPieces(), downloadedBytes.get());
                        }
                    } else {
                        logger.warning("Corrupted piece #" + pieceIndex + " received from " + peer + ". Discarding.");
                        scheduler.markFailed(pieceIndex);
                    }
                } else {
                    scheduler.markFailed(pieceIndex);
                }
            } catch (Exception e) {
                scheduler.markFailed(pieceIndex);
                break; // Connection issue, break to avoid hammering dead peer
            }
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            downloadPool.shutdownNow();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getCompletedPieces() {
        return scheduler.getCompletedPieceCount();
    }

    public long getDownloadedBytes() {
        return downloadedBytes.get();
    }
}
