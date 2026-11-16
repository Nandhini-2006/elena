package com.p2p.filedist;

import com.p2p.filedist.integrity.IntegrityManager;
import com.p2p.filedist.model.FileMetadata;
import com.p2p.filedist.storage.FileChunker;
import com.p2p.filedist.storage.StorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class StorageAndIntegrityTest {

    @Test
    public void testChunkingAndIntegrity(@TempDir Path tempDir) throws IOException {
        Path sampleFile = tempDir.resolve("sample.bin");
        byte[] data = new byte[150 * 1024]; // 150 KB
        Arrays.fill(data, (byte) 7);
        Files.write(sampleFile, data);

        int pieceSize = 64 * 1024; // 64 KB pieces -> 3 pieces
        FileMetadata meta = FileChunker.chunkFile(sampleFile, pieceSize);

        assertEquals(3, meta.getTotalPieces());
        assertEquals(150 * 1024, meta.getFileSize());
        assertEquals(64 * 1024, meta.getPieceSize());
        assertEquals(3, meta.getPieceHashes().size());

        // Validate individual piece lengths
        assertEquals(64 * 1024, meta.getPieceInfo(0).getLength());
        assertEquals(64 * 1024, meta.getPieceInfo(1).getLength());
        assertEquals(22 * 1024, meta.getPieceInfo(2).getLength());

        // Test metadata serialization & deserialization
        Path metaPath = tempDir.resolve("sample.bin.meta");
        meta.saveToFile(metaPath);

        FileMetadata loaded = FileMetadata.loadFromFile(metaPath);
        assertEquals(meta.getFileId(), loaded.getFileId());
        assertEquals(meta.getFileHash(), loaded.getFileHash());
        assertEquals(meta.getTotalPieces(), loaded.getTotalPieces());
    }

    @Test
    public void testStoragePositionalReadWrite(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("source.bin");
        byte[] sourceData = new byte[128 * 1024];
        for (int i = 0; i < sourceData.length; i++) {
            sourceData[i] = (byte) (i % 127);
        }
        Files.write(sourcePath, sourceData);

        FileMetadata meta = FileChunker.chunkFile(sourcePath, 64 * 1024);

        try (StorageManager storage = new StorageManager(tempDir.resolve("storage"))) {
            storage.registerExistingFile(meta, sourcePath);

            byte[] piece0 = storage.readPiece(meta.getFileId(), 0);
            byte[] piece1 = storage.readPiece(meta.getFileId(), 1);

            assertEquals(64 * 1024, piece0.length);
            assertEquals(64 * 1024, piece1.length);
            assertTrue(IntegrityManager.verifyPiece(piece0, meta.getPieceInfo(0).getExpectedSha256()));
            assertTrue(IntegrityManager.verifyPiece(piece1, meta.getPieceInfo(1).getExpectedSha256()));

            // Write pieces out of order to new destination file
            Path destPath = tempDir.resolve("reconstructed.bin");
            storage.initDownload(meta, destPath);

            // Write piece 1 first, then piece 0
            storage.writePiece(meta.getFileId(), 1, piece1);
            assertFalse(storage.isFileComplete(meta.getFileId()));

            storage.writePiece(meta.getFileId(), 0, piece0);
            assertTrue(storage.isFileComplete(meta.getFileId()));

            byte[] reconstructed = Files.readAllBytes(destPath);
            assertArrayEquals(sourceData, reconstructed);
            assertTrue(IntegrityManager.verifyFile(destPath, meta.getFileHash()));
        }
    }
}
