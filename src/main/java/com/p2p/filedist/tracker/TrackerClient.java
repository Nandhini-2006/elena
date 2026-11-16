package com.p2p.filedist.tracker;

import com.p2p.filedist.model.PeerInfo;
import com.p2p.filedist.net.NioClient;
import com.p2p.filedist.protocol.Message;
import com.p2p.filedist.protocol.MessageCodec;
import com.p2p.filedist.protocol.MessageType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class TrackerClient {
    private final String trackerHost;
    private final int trackerPort;

    public TrackerClient(String trackerHost, int trackerPort) {
        this.trackerHost = trackerHost;
        this.trackerPort = trackerPort;
    }

    public List<PeerInfo> announce(String peerId, String fileId, int listeningPort, String event, byte[] bitfield) throws IOException {
        Message request = MessageCodec.createAnnounce(peerId, fileId, listeningPort, event, bitfield);
        Message response = NioClient.sendAndReceive(trackerHost, trackerPort, request, 5000);

        if (response.getType() == MessageType.PEER_LIST) {
            return MessageCodec.decodePeerList(response.getPayload());
        } else if (response.getType() == MessageType.ERROR) {
            MessageCodec.ErrorPayload err = MessageCodec.decodeError(response.getPayload());
            throw new IOException("Tracker returned error: [" + err.code() + "] " + err.message());
        }

        return Collections.emptyList();
    }

    public void heartbeat(String peerId) {
        try {
            Message request = MessageCodec.createHeartbeat(peerId);
            NioClient.sendAndReceive(trackerHost, trackerPort, request, 3000);
        } catch (Exception ignored) {
            // Heartbeat failures are non-fatal for peer operation
        }
    }
}
