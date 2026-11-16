package com.p2p.filedist.protocol;

import com.p2p.filedist.model.PeerInfo;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public final class MessageCodec {
    private MessageCodec() {}

    // --- Handshake ---
    public static Message createHandshake(String peerId, int port) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(peerId);
            dos.writeInt(port);
            return new Message(MessageType.HANDSHAKE, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record HandshakePayload(String peerId, int port) {}

    public static HandshakePayload decodeHandshake(byte[] payload) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            return new HandshakePayload(dis.readUTF(), dis.readInt());
        }
    }

    // --- Announce ---
    public static Message createAnnounce(String peerId, String fileId, int port, String event, byte[] bitfield) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(peerId);
            dos.writeUTF(fileId);
            dos.writeInt(port);
            dos.writeUTF(event);
            dos.writeInt(bitfield != null ? bitfield.length : 0);
            if (bitfield != null && bitfield.length > 0) {
                dos.write(bitfield);
            }
            return new Message(MessageType.ANNOUNCE, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record AnnouncePayload(String peerId, String fileId, int port, String event, byte[] bitfield) {}

    public static AnnouncePayload decodeAnnounce(byte[] payload) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            String peerId = dis.readUTF();
            String fileId = dis.readUTF();
            int port = dis.readInt();
            String event = dis.readUTF();
            int bfLen = dis.readInt();
            byte[] bitfield = new byte[bfLen];
            if (bfLen > 0) {
                dis.readFully(bitfield);
            }
            return new AnnouncePayload(peerId, fileId, port, event, bitfield);
        }
    }

    // --- Peer List ---
    public static Message createPeerList(long reqId, List<PeerInfo> peers) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(peers.size());
            for (PeerInfo p : peers) {
                p.writeTo(dos);
            }
            return new Message(MessageType.PEER_LIST, reqId, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<PeerInfo> decodePeerList(byte[] payload) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            int count = dis.readInt();
            List<PeerInfo> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(PeerInfo.readFrom(dis));
            }
            return list;
        }
    }

    // --- Have Piece ---
    public static Message createHavePiece(String fileId, int pieceIndex) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(fileId);
            dos.writeInt(pieceIndex);
            return new Message(MessageType.HAVE_PIECE, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record HavePiecePayload(String fileId, int pieceIndex) {}

    public static HavePiecePayload decodeHavePiece(byte[] payload) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            return new HavePiecePayload(dis.readUTF(), dis.readInt());
        }
    }

    // --- Request Piece ---
    public static Message createRequestPiece(String fileId, int pieceIndex) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(fileId);
            dos.writeInt(pieceIndex);
            return new Message(MessageType.REQUEST_PIECE, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record RequestPiecePayload(String fileId, int pieceIndex) {}

    public static RequestPiecePayload decodeRequestPiece(byte[] payload) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            return new RequestPiecePayload(dis.readUTF(), dis.readInt());
        }
    }

    // --- Piece Data ---
    public static Message createPieceData(long reqId, String fileId, int pieceIndex, byte[] pieceBytes) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(fileId);
            dos.writeInt(pieceIndex);
            dos.writeInt(pieceBytes.length);
            dos.write(pieceBytes);
            return new Message(MessageType.PIECE_DATA, reqId, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record PieceDataPayload(String fileId, int pieceIndex, byte[] data) {}

    public static PieceDataPayload decodePieceData(byte[] payload) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            String fileId = dis.readUTF();
            int pieceIndex = dis.readInt();
            int len = dis.readInt();
            byte[] data = new byte[len];
            dis.readFully(data);
            return new PieceDataPayload(fileId, pieceIndex, data);
        }
    }

    // --- Bitfield ---
    public static Message createBitfield(String fileId, byte[] bitfield) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(fileId);
            dos.writeInt(bitfield.length);
            dos.write(bitfield);
            return new Message(MessageType.BITFIELD, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record BitfieldPayload(String fileId, byte[] bitfield) {}

    public static BitfieldPayload decodeBitfield(byte[] payload) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            String fileId = dis.readUTF();
            int len = dis.readInt();
            byte[] bitfield = new byte[len];
            dis.readFully(bitfield);
            return new BitfieldPayload(fileId, bitfield);
        }
    }

    // --- Heartbeat ---
    public static Message createHeartbeat(String peerId) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeUTF(peerId);
            return new Message(MessageType.HEARTBEAT, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String decodeHeartbeat(byte[] payload) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            return dis.readUTF();
        }
    }

    // --- Error ---
    public static Message createError(long reqId, int code, String message) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(code);
            dos.writeUTF(message);
            return new Message(MessageType.ERROR, reqId, baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record ErrorPayload(int code, String message) {}

    public static ErrorPayload decodeError(byte[] payload) throws IOException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            return new ErrorPayload(dis.readInt(), dis.readUTF());
        }
    }
}
