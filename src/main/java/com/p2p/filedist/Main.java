package com.p2p.filedist;

import com.p2p.filedist.cli.PeerCli;
import com.p2p.filedist.common.Constants;
import com.p2p.filedist.integrity.IntegrityManager;
import com.p2p.filedist.model.FileMetadata;
import com.p2p.filedist.peer.PeerNode;
import com.p2p.filedist.tracker.TrackerServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            printHelp();
            return;
        }

        String mode = args[0].toLowerCase();
        try {
            switch (mode) {
                case "tracker" -> {
                    int port = args.length > 1 ? Integer.parseInt(args[1]) : Constants.DEFAULT_TRACKER_PORT;
                    try (TrackerServer server = new TrackerServer("0.0.0.0", port)) {
                        server.start();
                        System.out.println(">>> Tracker Server running on port " + server.getPort() + ". Press Ctrl+C to stop.");
                        Thread.currentThread().join();
                    }
                }
                case "peer" -> {
                    String peerId = args.length > 1 ? args[1] : "PEER-" + System.currentTimeMillis() % 10000;
                    int port = args.length > 2 ? Integer.parseInt(args[2]) : 0;
                    String trackerHost = args.length > 3 ? args[3] : "127.0.0.1";
                    int trackerPort = args.length > 4 ? Integer.parseInt(args[4]) : Constants.DEFAULT_TRACKER_PORT;
                    Path storageDir = args.length > 5 ? Paths.get(args[5]) : Paths.get("./peer_storage_" + peerId);

                    try (PeerNode node = new PeerNode(peerId, port, trackerHost, trackerPort, storageDir)) {
                        node.start();
                        PeerCli cli = new PeerCli(node);
                        cli.run();
                    }
                }
                case "demo" -> {
                    runDemoSwarm();
                }
                default -> printHelp();
            }
        } catch (Exception e) {
            System.err.println("Fatal execution error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printHelp() {
        System.out.println("==================================================================");
        System.out.println("   Peer-to-Peer (P2P) File Distribution System");
        System.out.println("   High-Performance Decentralized Engine with Java NIO");
        System.out.println("==================================================================");
        System.out.println("Usage:");
        System.out.println("  java -jar p2p.jar tracker [port]");
        System.out.println("  java -jar p2p.jar peer [peerId] [port] [trackerHost] [trackerPort] [storageDir]");
        System.out.println("  java -jar p2p.jar demo");
        System.out.println("==================================================================");
    }

    private static void runDemoSwarm() throws Exception {
        System.out.println("==================================================================");
        System.out.println("   RUNNING AUTOMATED MULTI-PEER SWARM DEMONSTRATION");
        System.out.println("==================================================================");

        Path tempDir = Files.createTempDirectory("p2p_demo_");
        Path seederDir = tempDir.resolve("seeder_A");
        Path leecherDirB = tempDir.resolve("leecher_B");
        Path leecherDirC = tempDir.resolve("leecher_C");

        Files.createDirectories(seederDir);
        Files.createDirectories(leecherDirB);
        Files.createDirectories(leecherDirC);

        // 1. Generate a 1 MB binary payload with known random seed
        Path testFile = seederDir.resolve("large_distribution_payload.dat");
        int fileSize = 1024 * 1024; // 1 MB
        byte[] payloadData = new byte[fileSize];
        Random rng = new Random(42);
        rng.nextBytes(payloadData);
        Files.write(testFile, payloadData);
        String originalSha256 = IntegrityManager.calculateHash(payloadData);

        System.out.println("1. Created 1MB Test File: " + testFile);
        System.out.println("   Original SHA-256: " + originalSha256);

        // 2. Start Tracker Server on dynamic port
        TrackerServer tracker = new TrackerServer("127.0.0.1", 0);
        tracker.start();
        int trackerPort = tracker.getPort();
        System.out.println("2. Tracker Server initialized on port: " + trackerPort);

        // 3. Start Peer A (Seeder)
        PeerNode seederA = new PeerNode("Node-A-Seeder", 0, "127.0.0.1", trackerPort, seederDir);
        seederA.start();
        FileMetadata metadata = seederA.publishFile(testFile);
        System.out.println("3. Peer A (Seeder) published file ID: " + metadata.getFileId()
                + " (" + metadata.getTotalPieces() + " pieces x " + metadata.getPieceSize() + " bytes)");

        // 4. Start Peer B (Leecher -> Seeder)
        PeerNode leecherB = new PeerNode("Node-B-Leecher", 0, "127.0.0.1", trackerPort, leecherDirB);
        leecherB.start();
        System.out.println("4. Peer B initialized on port " + leecherB.getBoundPort() + " - starting download...");

        Path destB = leecherDirB.resolve("reconstructed_B.dat");
        CompletableFuture<Path> futureB = leecherB.downloadFile(metadata, destB, 4,
                (verified, total, bytes) -> System.out.printf("\r[Peer B Progress] %d/%d pieces (%d bytes)", verified, total, bytes));

        Path downloadedB = futureB.get();
        System.out.println("\n✓ Peer B download finished!");
        boolean validB = IntegrityManager.verifyFile(downloadedB, originalSha256);
        System.out.println("  Peer B Bit-for-bit Integrity Verification: " + (validB ? "PASSED (MATCH)" : "FAILED"));

        // 5. Start Peer C (Leecher downloading simultaneously from Peer A and Peer B!)
        PeerNode leecherC = new PeerNode("Node-C-Leecher", 0, "127.0.0.1", trackerPort, leecherDirC);
        leecherC.start();
        System.out.println("5. Peer C initialized on port " + leecherC.getBoundPort() + " - downloading in parallel from Swarm (Node A + Node B)...");

        Path destC = leecherDirC.resolve("reconstructed_C.dat");
        CompletableFuture<Path> futureC = leecherC.downloadFile(metadata, destC, 4,
                (verified, total, bytes) -> System.out.printf("\r[Peer C Swarm Progress] %d/%d pieces (%d bytes)", verified, total, bytes));

        Path downloadedC = futureC.get();
        System.out.println("\n✓ Peer C swarm download finished!");
        boolean validC = IntegrityManager.verifyFile(downloadedC, originalSha256);
        System.out.println("  Peer C Bit-for-bit Integrity Verification: " + (validC ? "PASSED (MATCH)" : "FAILED"));

        System.out.println("------------------------------------------------------------------");
        System.out.println("Swarm Upload Statistics:");
        System.out.println("  Node A Uploaded: " + seederA.getUploadManager().getUploadedBytes() + " bytes");
        System.out.println("  Node B Uploaded: " + leecherB.getUploadManager().getUploadedBytes() + " bytes");
        System.out.println("------------------------------------------------------------------");

        // Cleanup
        leecherC.close();
        leecherB.close();
        seederA.close();
        tracker.close();

        System.out.println("✓ Swarm demonstration complete! All nodes exited cleanly.");
    }
}
