package com.p2p.filedist.cli;

import com.p2p.filedist.model.FileMetadata;
import com.p2p.filedist.peer.DownloadManager;
import com.p2p.filedist.peer.PeerNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class PeerCli {
    private final PeerNode peerNode;

    public PeerCli(PeerNode peerNode) {
        this.peerNode = peerNode;
    }

    public void run() {
        System.out.println("==================================================================");
        System.out.println("   P2P File Distribution Client - Java NIO Engine");
        System.out.println("   Peer ID: " + peerNode.getPeerId() + " | Listening Port: " + peerNode.getBoundPort());
        System.out.println("==================================================================");
        System.out.println("Commands:");
        System.out.println("  publish <filepath>             - Chunk file, create .meta, and seed");
        System.out.println("  download <metapath> [destpath]  - Download file concurrently from peers");
        System.out.println("  status                         - Check upload/download status");
        System.out.println("  help                           - Show commands");
        System.out.println("  exit                           - Disconnect and exit");
        System.out.println("------------------------------------------------------------------");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while (true) {
                System.out.print("p2p> ");
                line = reader.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                String command = parts[0].toLowerCase();

                try {
                    switch (command) {
                        case "publish" -> {
                            if (parts.length < 2) {
                                System.out.println("Usage: publish <filepath>");
                                break;
                            }
                            Path filePath = Paths.get(parts[1]).toAbsolutePath().normalize();
                            System.out.println("Chunking and hashing file: " + filePath + " ...");
                            FileMetadata meta = peerNode.publishFile(filePath);
                            Path metaSavePath = filePath.resolveSibling(filePath.getFileName().toString() + ".meta");
                            meta.saveToFile(metaSavePath);
                            System.out.println("✓ File published successfully!");
                            System.out.println("  File ID:      " + meta.getFileId());
                            System.out.println("  Size:         " + meta.getFileSize() + " bytes");
                            System.out.println("  Pieces:       " + meta.getTotalPieces() + " (" + meta.getPieceSize() + " bytes each)");
                            System.out.println("  File Hash:    " + meta.getFileHash());
                            System.out.println("  Meta saved:   " + metaSavePath);
                        }
                        case "download" -> {
                            if (parts.length < 2) {
                                System.out.println("Usage: download <metapath> [destpath]");
                                break;
                            }
                            Path metaPath = Paths.get(parts[1]).toAbsolutePath().normalize();
                            FileMetadata meta = FileMetadata.loadFromFile(metaPath);
                            Path destPath;
                            if (parts.length >= 3) {
                                destPath = Paths.get(parts[2]).toAbsolutePath().normalize();
                            } else {
                                destPath = metaPath.resolveSibling("downloaded_" + meta.getFileName());
                            }

                            System.out.println("Initiating download for " + meta.getFileName() + " (" + meta.getFileSize() + " bytes, " + meta.getTotalPieces() + " pieces)...");
                            CompletableFuture<Path> future = peerNode.downloadFile(meta, destPath, 4, new DownloadManager.DownloadProgressListener() {
                                @Override
                                public void onProgress(int verifiedPieces, int totalPieces, long bytesDownloaded) {
                                    double pct = (double) verifiedPieces / totalPieces * 100.0;
                                    System.out.printf("\rProgress: [%d/%d pieces] %.1f%% (%d bytes)", verifiedPieces, totalPieces, pct, bytesDownloaded);
                                }

                                @Override
                                public void onComplete(Path savedPath, String fileHash) {
                                    System.out.println("\n✓ Download completed and SHA-256 verified!");
                                    System.out.println("  Saved to: " + savedPath);
                                }

                                @Override
                                public void onError(String message, Throwable cause) {
                                    System.out.println("\n✗ Download error: " + message);
                                }
                            });
                            future.join();
                        }
                        case "status" -> {
                            System.out.println("Node status:");
                            System.out.println("  Peer ID:         " + peerNode.getPeerId());
                            System.out.println("  Port:            " + peerNode.getBoundPort());
                            System.out.println("  Uploaded bytes:  " + peerNode.getUploadManager().getUploadedBytes());
                        }
                        case "help" -> {
                            System.out.println("Available commands: publish, download, status, help, exit");
                        }
                        case "exit", "quit" -> {
                            System.out.println("Shutting down peer node...");
                            peerNode.close();
                            return;
                        }
                        default -> System.out.println("Unknown command: " + command + ". Type 'help' for usage.");
                    }
                } catch (Exception e) {
                    System.out.println("Command failed: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("CLI error: " + e.getMessage());
        }
    }
}
