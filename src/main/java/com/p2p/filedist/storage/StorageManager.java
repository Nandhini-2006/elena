package com.p2p.filedist.storage;

import com.p2p.filedist.model.Bitfield;
import com.p2p.filedist.model.FileMetadata;
import com.p2p.filedist.model.PieceInfo;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StorageManager implements Closeable {

    public static class ManagedFile {
        private final FileMetadata metadata;
        private final Path path;
        private final FileChannel channel;
        private final Bitfield bitfield;

        public ManagedFile(FileMetadata metadata, Path path, FileChannel channel, Bitfield bitfield) {
            this.metadata = metadata;
            this.path = path;
            this.channel = channel;
            this.bitfield = bitfield;
        }

        public FileMetadata getMetadata() {
            return metadata;
        }

        public Path getPath() {
            return path;
        }

        public FileChannel getChannel() {
            return channel;
        }

        public Bitfield getBitfield() {
            return bitfield;
        }
    }

    private final Path rootDirectory;
    private final Map<String, ManagedFile> files = new ConcurrentHashMap<>();

    public StorageManager(Path rootDirectory) throws IOException {
        this.rootDirectory = rootDirectory;
        if (!Files.exists(rootDirectory)) {
            Files.createDirectories(rootDirectory);
        }
    }

    public synchronized ManagedFile registerExistingFile(FileMetadata metadata, Path filePath) throws IOException {
        FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ);
        Bitfield bitfield = new Bitfield(metadata.getTotalPieces(), true); // seeder has all pieces
        ManagedFile mf = new ManagedFile(metadata, filePath, channel, bitfield);
        files.put(metadata.getFileId(), mf);
        return mf;
    }

    public synchronized ManagedFile initDownload(FileMetadata metadata, Path destinationPath) throws IOException {
        if (destinationPath.getParent() != null && !Files.exists(destinationPath.getParent())) {
            Files.createDirectories(destinationPath.getParent());
        }

        // Open channel with READ, WRITE, CREATE
        FileChannel channel = FileChannel.open(destinationPath,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE);

        // Preallocate file size
        channel.position(metadata.getFileSize() - 1);
        channel.write(ByteBuffer.wrap(new byte[]{0}));
        channel.force(true);

        Bitfield bitfield = new Bitfield(metadata.getTotalPieces(), false);
        ManagedFile mf = new ManagedFile(metadata, destinationPath, channel, bitfield);
        files.put(metadata.getFileId(), mf);
        return mf;
    }

    public byte[] readPiece(String fileId, int pieceIndex) throws IOException {
        ManagedFile mf = files.get(fileId);
        if (mf == null) {
            throw new IllegalArgumentException("File not managed: " + fileId);
        }

        PieceInfo pieceInfo = mf.metadata.getPieceInfo(pieceIndex);
        ByteBuffer buffer = ByteBuffer.allocate(pieceInfo.getLength());

        long offset = pieceInfo.getOffset();
        int expectedLen = pieceInfo.getLength();
        int bytesRead = 0;

        while (bytesRead < expectedLen) {
            int r = mf.channel.read(buffer, offset + bytesRead);
            if (r == -1) break;
            bytesRead += r;
        }

        buffer.flip();
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    public void writePiece(String fileId, int pieceIndex, byte[] data) throws IOException {
        ManagedFile mf = files.get(fileId);
        if (mf == null) {
            throw new IllegalArgumentException("File not managed: " + fileId);
        }

        PieceInfo pieceInfo = mf.metadata.getPieceInfo(pieceIndex);
        if (data.length != pieceInfo.getLength()) {
            throw new IllegalArgumentException("Data length " + data.length + " does not match expected " + pieceInfo.getLength());
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        long offset = pieceInfo.getOffset();
        int bytesWritten = 0;

        while (bytesWritten < data.length) {
            int w = mf.channel.write(buffer, offset + bytesWritten);
            bytesWritten += w;
        }

        mf.channel.force(false);
        mf.bitfield.setPiece(pieceIndex, true);
    }

    public Bitfield getBitfield(String fileId) {
        ManagedFile mf = files.get(fileId);
        return mf != null ? mf.bitfield : null;
    }

    public ManagedFile getManagedFile(String fileId) {
        return files.get(fileId);
    }

    public boolean isFileComplete(String fileId) {
        ManagedFile mf = files.get(fileId);
        return mf != null && mf.bitfield.isComplete();
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    @Override
    public synchronized void close() throws IOException {
        for (ManagedFile mf : files.values()) {
            try {
                if (mf.channel.isOpen()) {
                    mf.channel.close();
                }
            } catch (IOException ignored) {}
        }
        files.clear();
    }
}
