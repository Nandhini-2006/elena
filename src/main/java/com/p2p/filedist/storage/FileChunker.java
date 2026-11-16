package com.p2p.filedist.storage;

import com.p2p.filedist.common.Constants;
import com.p2p.filedist.integrity.IntegrityManager;
import com.p2p.filedist.model.FileMetadata;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FileChunker {

    public static FileMetadata chunkFile(Path filePath) throws IOException {
        return chunkFile(filePath, Constants.DEFAULT_PIECE_SIZE);
    }

    public static FileMetadata chunkFile(Path filePath, int pieceSize) throws IOException {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("File does not exist or is not a regular file: " + filePath);
        }

        long fileSize = Files.size(filePath);
        if (fileSize <= 0) {
            throw new IllegalArgumentException("File must not be empty");
        }

        int totalPieces = (int) ((fileSize + pieceSize - 1) / pieceSize);
        List<String> pieceHashes = new ArrayList<>(totalPieces);

        ByteBuffer buffer = ByteBuffer.allocate(pieceSize);
        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            for (int i = 0; i < totalPieces; i++) {
                buffer.clear();
                long offset = (long) i * pieceSize;
                int expectedLength = (int) Math.min((long) pieceSize, fileSize - offset);
                buffer.limit(expectedLength);

                int bytesRead = 0;
                while (bytesRead < expectedLength) {
                    int r = channel.read(buffer, offset + bytesRead);
                    if (r == -1) break;
                    bytesRead += r;
                }

                buffer.flip();
                String pieceHash = IntegrityManager.calculateHash(buffer);
                pieceHashes.add(pieceHash);
            }
        }

        String overallFileHash = IntegrityManager.calculateFileHash(filePath);
        String fileId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String fileName = filePath.getFileName().toString();

        return new FileMetadata(fileId, fileName, fileSize, pieceSize, totalPieces, overallFileHash, pieceHashes);
    }
}
