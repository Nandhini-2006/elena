package com.p2p.filedist.model;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileMetadata {
    private final String fileId;
    private final String fileName;
    private final long fileSize;
    private final int pieceSize;
    private final int totalPieces;
    private final String fileHash;
    private final List<String> pieceHashes;

    public FileMetadata(String fileId, String fileName, long fileSize, int pieceSize,
                        int totalPieces, String fileHash, List<String> pieceHashes) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
        this.totalPieces = totalPieces;
        this.fileHash = fileHash;
        this.pieceHashes = Collections.unmodifiableList(new ArrayList<>(pieceHashes));
    }

    public String getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getPieceSize() {
        return pieceSize;
    }

    public int getTotalPieces() {
        return totalPieces;
    }

    public String getFileHash() {
        return fileHash;
    }

    public List<String> getPieceHashes() {
        return pieceHashes;
    }

    public PieceInfo getPieceInfo(int index) {
        if (index < 0 || index >= totalPieces) {
            throw new IndexOutOfBoundsException("Invalid piece index: " + index);
        }
        long offset = (long) index * pieceSize;
        int length = (index == totalPieces - 1)
                ? (int) (fileSize - offset)
                : pieceSize;
        return new PieceInfo(index, offset, length, pieceHashes.get(index));
    }

    public byte[] toBytes() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(fileId);
            dos.writeUTF(fileName);
            dos.writeLong(fileSize);
            dos.writeInt(pieceSize);
            dos.writeInt(totalPieces);
            dos.writeUTF(fileHash);
            dos.writeInt(pieceHashes.size());
            for (String hash : pieceHashes) {
                dos.writeUTF(hash);
            }
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Serialization failure", e);
        }
    }

    public static FileMetadata fromBytes(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {
            String fileId = dis.readUTF();
            String fileName = dis.readUTF();
            long fileSize = dis.readLong();
            int pieceSize = dis.readInt();
            int totalPieces = dis.readInt();
            String fileHash = dis.readUTF();
            int hashCount = dis.readInt();
            List<String> hashes = new ArrayList<>(hashCount);
            for (int i = 0; i < hashCount; i++) {
                hashes.add(dis.readUTF());
            }
            return new FileMetadata(fileId, fileName, fileSize, pieceSize, totalPieces, fileHash, hashes);
        }
    }

    public void saveToFile(Path path) throws IOException {
        Files.write(path, toBytes());
    }

    public static FileMetadata loadFromFile(Path path) throws IOException {
        return fromBytes(Files.readAllBytes(path));
    }

    @Override
    public String toString() {
        return "FileMetadata{" +
                "fileId='" + fileId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize + " bytes" +
                ", pieceSize=" + pieceSize + " bytes" +
                ", totalPieces=" + totalPieces +
                ", fileHash='" + fileHash + '\'' +
                '}';
    }
}
