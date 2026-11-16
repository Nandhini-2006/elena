package com.p2p.filedist.integrity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class IntegrityManager {
    private static final String ALGORITHM = "SHA-256";

    public static String calculateHash(byte[] data) {
        return calculateHash(data, 0, data.length);
    }

    public static String calculateHash(byte[] data, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(data, offset, length);
            byte[] hash = digest.digest();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static String calculateHash(ByteBuffer buffer) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(buffer.duplicate());
            byte[] hash = digest.digest();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static String calculateFileHash(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static boolean verifyPiece(byte[] data, String expectedHash) {
        if (data == null || expectedHash == null) {
            return false;
        }
        String actualHash = calculateHash(data);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    public static boolean verifyFile(Path filePath, String expectedHash) throws IOException {
        if (!Files.exists(filePath) || expectedHash == null) {
            return false;
        }
        String actualHash = calculateFileHash(filePath);
        return actualHash.equalsIgnoreCase(expectedHash);
    }
}
