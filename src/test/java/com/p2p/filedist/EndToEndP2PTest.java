package com.p2p.filedist;

import com.p2p.filedist.integrity.IntegrityManager;
import com.p2p.filedist.model.FileMetadata;
import com.p2p.filedist.peer.PeerNode;
import com.p2p.filedist.tracker.TrackerServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class EndToEndP2PTest {

    @Test
    public void testTwoPeerTransfer(@TempDir Path tempDir) throws Exception {
        Path seederDir = tempDir.resolve("seeder");
        Path leecherDir = tempDir.resolve("leecher");
        Files.createDirectories(seederDir);
        Files.createDirectories(leecherDir);

        // 1. Create a 256 KB test file
        Path sourceFile = seederDir.resolve("video.mp4");
        byte[] originalBytes = new byte[256 * 1024];
        new Random(12345).nextBytes(originalBytes);
        Files.write(sourceFile, originalBytes);
        String originalHash = IntegrityManager.calculateHash(originalBytes);

        // 2. Start Tracker Server
        TrackerServer tracker = new TrackerServer("127.0.0.1", 0);
        tracker.start();
        int trackerPort = tracker.getPort();

        // 3. Start Seeder Peer
        PeerNode seeder = new PeerNode("Seeder-A", 0, "127.0.0.1", trackerPort, seederDir);
        seeder.start();
        FileMetadata metadata = seeder.publishFile(sourceFile);

        // 4. Start Leecher Peer
        PeerNode leecher = new PeerNode("Leecher-B", 0, "127.0.0.1", trackerPort, leecherDir);
        leecher.start();

        Path destPath = leecherDir.resolve("downloaded_video.mp4");
        CompletableFuture<Path> downloadFuture = leecher.downloadFile(metadata, destPath, 2, null);

        Path finishedPath = downloadFuture.get(15, TimeUnit.SECONDS);
        assertNotNull(finishedPath);
        assertTrue(Files.exists(finishedPath));

        // 5. Verify bit-for-bit integrity
        byte[] downloadedBytes = Files.readAllBytes(finishedPath);
        assertArrayEquals(originalBytes, downloadedBytes);
        assertTrue(IntegrityManager.verifyFile(finishedPath, originalHash));

        // 6. Clean shutdown
        leecher.close();
        seeder.close();
        tracker.close();
    }
}
