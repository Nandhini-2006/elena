package com.p2p.filedist;

import com.p2p.filedist.common.Constants;
import com.p2p.filedist.model.Bitfield;
import com.p2p.filedist.model.PeerInfo;
import com.p2p.filedist.protocol.Message;
import com.p2p.filedist.protocol.MessageCodec;
import com.p2p.filedist.protocol.MessageHeader;
import com.p2p.filedist.protocol.MessageType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProtocolTest {

    @Test
    public void testHeaderSerialization() {
        MessageHeader header = new MessageHeader(MessageType.REQUEST_PIECE, 1024, 9999L);
        ByteBuffer buffer = ByteBuffer.allocate(Constants.HEADER_SIZE);
        header.writeTo(buffer);
        buffer.flip();

        MessageHeader decoded = MessageHeader.readFrom(buffer);
        assertEquals(Constants.PROTOCOL_MAGIC, decoded.getMagic());
        assertEquals(Constants.PROTOCOL_VERSION, decoded.getVersion());
        assertEquals(MessageType.REQUEST_PIECE, decoded.getType());
        assertEquals(1024, decoded.getPayloadLength());
        assertEquals(9999L, decoded.getRequestId());
    }

    @Test
    public void testHandshakeCodec() throws IOException {
        Message msg = MessageCodec.createHandshake("Peer-42", 8080);
        MessageCodec.HandshakePayload payload = MessageCodec.decodeHandshake(msg.getPayload());

        assertEquals("Peer-42", payload.peerId());
        assertEquals(8080, payload.port());
    }

    @Test
    public void testAnnounceAndPeerListCodec() throws IOException {
        Bitfield bitfield = new Bitfield(16, true);
        Message announceMsg = MessageCodec.createAnnounce("Peer-1", "File-ABC", 9000, "STARTED", bitfield.toByteArray());
        MessageCodec.AnnouncePayload announce = MessageCodec.decodeAnnounce(announceMsg.getPayload());

        assertEquals("Peer-1", announce.peerId());
        assertEquals("File-ABC", announce.fileId());
        assertEquals(9000, announce.port());
        assertEquals("STARTED", announce.event());
        assertNotNull(announce.bitfield());

        PeerInfo peer1 = new PeerInfo("P1", "127.0.0.1", 9001, bitfield.toByteArray());
        PeerInfo peer2 = new PeerInfo("P2", "127.0.0.1", 9002, bitfield.toByteArray());
        Message peerListMsg = MessageCodec.createPeerList(12345L, List.of(peer1, peer2));

        List<PeerInfo> peers = MessageCodec.decodePeerList(peerListMsg.getPayload());
        assertEquals(2, peers.size());
        assertEquals("P1", peers.get(0).getPeerId());
        assertEquals(9001, peers.get(0).getPort());
        assertEquals("P2", peers.get(1).getPeerId());
        assertEquals(9002, peers.get(1).getPort());
    }

    @Test
    public void testPieceDataCodec() throws IOException {
        byte[] dummyData = new byte[]{1, 2, 3, 4, 5, 42, 99};
        Message msg = MessageCodec.createPieceData(777L, "File-XYZ", 3, dummyData);

        MessageCodec.PieceDataPayload pieceData = MessageCodec.decodePieceData(msg.getPayload());
        assertEquals("File-XYZ", pieceData.fileId());
        assertEquals(3, pieceData.pieceIndex());
        assertArrayEquals(dummyData, pieceData.data());
    }

    @Test
    public void testBitfieldOperations() {
        Bitfield bf = new Bitfield(10);
        assertFalse(bf.hasPiece(0));
        assertFalse(bf.isComplete());

        bf.setPiece(0, true);
        bf.setPiece(5, true);
        bf.setPiece(9, true);

        assertEquals(3, bf.getCompletedCount());
        assertTrue(bf.hasPiece(0));
        assertTrue(bf.hasPiece(5));
        assertTrue(bf.hasPiece(9));
        assertFalse(bf.hasPiece(1));

        byte[] serialized = bf.toByteArray();
        Bitfield deserialized = Bitfield.fromByteArray(serialized, 10);
        assertEquals(3, deserialized.getCompletedCount());
        assertTrue(deserialized.hasPiece(0));
        assertTrue(deserialized.hasPiece(5));
        assertTrue(deserialized.hasPiece(9));
    }
}
