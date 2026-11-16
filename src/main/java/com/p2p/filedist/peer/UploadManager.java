package com.p2p.filedist.peer;

import com.p2p.filedist.model.Bitfield;
import com.p2p.filedist.net.NioConnection;
import com.p2p.filedist.protocol.Message;
import com.p2p.filedist.protocol.MessageCodec;
import com.p2p.filedist.storage.StorageManager;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class UploadManager {
    private static final Logger logger = Logger.getLogger(UploadManager.class.getName());

    private final StorageManager storageManager;
    private final String peerId;
    private final int listeningPort;
    private final AtomicLong uploadedBytes = new AtomicLong(0);

    public UploadManager(String peerId, int listeningPort, StorageManager storageManager) {
        this.peerId = peerId;
        this.listeningPort = listeningPort;
        this.storageManager = storageManager;
    }

    public void handleHandshake(NioConnection connection, Message message) {
        try {
            MessageCodec.HandshakePayload hs = MessageCodec.decodeHandshake(message.getPayload());
            logger.fine("Received handshake from peer: " + hs.peerId() + ":" + hs.port());
            // Reply with our own handshake
            connection.send(MessageCodec.createHandshake(peerId, listeningPort));
        } catch (IOException e) {
            logger.warning("Error processing handshake: " + e.getMessage());
        }
    }

    public void handleRequestPiece(NioConnection connection, Message message) {
        try {
            MessageCodec.RequestPiecePayload req = MessageCodec.decodeRequestPiece(message.getPayload());
            Bitfield bitfield = storageManager.getBitfield(req.fileId());

            if (bitfield == null || !bitfield.hasPiece(req.pieceIndex())) {
                connection.send(MessageCodec.createError(message.getRequestId(), 404, "Piece not available: " + req.pieceIndex()));
                return;
            }

            byte[] pieceData = storageManager.readPiece(req.fileId(), req.pieceIndex());
            Message response = MessageCodec.createPieceData(message.getRequestId(), req.fileId(), req.pieceIndex(), pieceData);
            connection.send(response);
            uploadedBytes.addAndGet(pieceData.length);
        } catch (Exception e) {
            logger.warning("Failed to serve piece request: " + e.getMessage());
            connection.send(MessageCodec.createError(message.getRequestId(), 500, "I/O error serving piece: " + e.getMessage()));
        }
    }

    public long getUploadedBytes() {
        return uploadedBytes.get();
    }
}
