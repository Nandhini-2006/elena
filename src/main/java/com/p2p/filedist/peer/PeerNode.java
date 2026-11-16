package com.p2p.filedist.peer;

import com.p2p.filedist.model.Bitfield;
import com.p2p.filedist.model.FileMetadata;
import com.p2p.filedist.net.MessageHandler;
import com.p2p.filedist.net.NioConnection;
import com.p2p.filedist.net.NioServer;
import com.p2p.filedist.protocol.Message;
import com.p2p.filedist.protocol.MessageType;
import com.p2p.filedist.storage.FileChunker;
import com.p2p.filedist.storage.StorageManager;
import com.p2p.filedist.tracker.TrackerClient;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class PeerNode implements MessageHandler, Closeable {
    private static final Logger logger = Logger.getLogger(PeerNode.class.getName());

    private final String peerId;
    private final int requestedPort;
    private final Path storageDirectory;
    private final String trackerHost;
    private final int trackerPort;

    private NioServer nioServer;
    private StorageManager storageManager;
    private UploadManager uploadManager;
    private TrackerClient trackerClient;
    private ScheduledExecutorService heartbeatExecutor;

    private int boundPort;

    public PeerNode(String trackerHost, int trackerPort, Path storageDirectory) {
        this("PEER-" + UUID.randomUUID().toString().substring(0, 8), 0, trackerHost, trackerPort, storageDirectory);
    }

    public PeerNode(String peerId, int requestedPort, String trackerHost, int trackerPort, Path storageDirectory) {
        this.peerId = peerId;
        this.requestedPort = requestedPort;
        this.trackerHost = trackerHost;
        this.trackerPort = trackerPort;
        this.storageDirectory = storageDirectory;
    }

    public synchronized void start() throws IOException {
        this.storageManager = new StorageManager(storageDirectory);
        this.nioServer = new NioServer("0.0.0.0", requestedPort, this);
        this.nioServer.start();
        this.boundPort = nioServer.getBoundPort();

        this.uploadManager = new UploadManager(peerId, boundPort, storageManager);
        this.trackerClient = new TrackerClient(trackerHost, trackerPort);

        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Peer-Heartbeat-" + peerId);
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            trackerClient.heartbeat(peerId);
        }, 10, 10, TimeUnit.SECONDS);

        logger.info("Peer " + peerId + " started on port " + boundPort);
    }

    public FileMetadata publishFile(Path sourceFilePath) throws IOException {
        FileMetadata metadata = FileChunker.chunkFile(sourceFilePath);
        storageManager.registerExistingFile(metadata, sourceFilePath);

        Bitfield fullBitfield = storageManager.getBitfield(metadata.getFileId());
        trackerClient.announce(peerId, metadata.getFileId(), boundPort, "STARTED", fullBitfield.toByteArray());

        logger.info("Published file " + metadata.getFileName() + " (ID: " + metadata.getFileId() + ", "
                + metadata.getTotalPieces() + " pieces)");
        return metadata;
    }

    public CompletableFuture<Path> downloadFile(FileMetadata metadata, Path destinationPath,
            int concurrency, DownloadManager.DownloadProgressListener listener) {
        DownloadManager downloadManager = new DownloadManager(peerId, boundPort, metadata,
                storageManager, trackerClient, concurrency);
        return downloadManager.startDownload(destinationPath, listener);
    }

    @Override
    public void onConnected(NioConnection connection) {
        // Incoming peer connection
    }

    @Override
    public void onMessage(NioConnection connection, Message message) {
        try {
            if (message.getType() == MessageType.HANDSHAKE) {
                uploadManager.handleHandshake(connection, message);
            } else if (message.getType() == MessageType.REQUEST_PIECE) {
                uploadManager.handleRequestPiece(connection, message);
            }
        } catch (Exception e) {
            logger.warning("Error processing message: " + e.getMessage());
        }
    }

    @Override
    public void onDisconnected(NioConnection connection, Exception reason) {
        // Disconnected
    }

    public String getPeerId() {
        return peerId;
    }

    public int getBoundPort() {
        return boundPort;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public TrackerClient getTrackerClient() {
        return trackerClient;
    }

    public UploadManager getUploadManager() {
        return uploadManager;
    }

        @Override
    public synchronized void close() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
        if (nioServer != null) {
            nioServer.close();
        }
        if (storageManager != null) {
            try {
                storageManager.close();
            } catch (IOException ignored) {}
        }
        logger.info("Peer " + peerId + " stopped.");
    }
}

