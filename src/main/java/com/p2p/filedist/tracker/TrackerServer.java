package com.p2p.filedist.tracker;

import com.p2p.filedist.common.Constants;
import com.p2p.filedist.model.PeerInfo;
import com.p2p.filedist.net.MessageHandler;
import com.p2p.filedist.net.NioConnection;
import com.p2p.filedist.net.NioServer;
import com.p2p.filedist.protocol.Message;
import com.p2p.filedist.protocol.MessageCodec;
import com.p2p.filedist.protocol.MessageType;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TrackerServer implements MessageHandler, Closeable {
    private static final Logger logger = Logger.getLogger(TrackerServer.class.getName());

    private final String host;
    private final int port;
    private final TrackerRegistry registry;
    private final ScheduledExecutorService scheduler;
    private NioServer nioServer;

    public TrackerServer(String host, int port) {
        this.host = host;
        this.port = port;
        this.registry = new TrackerRegistry();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Tracker-Eviction-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() throws IOException {
        this.nioServer = new NioServer(host, port, this);
        this.nioServer.start();

        // Schedule periodic stale peer eviction
        scheduler.scheduleAtFixedRate(() -> {
            int evicted = registry.evictStalePeers(Constants.TRACKER_HEARTBEAT_TIMEOUT_MS);
            if (evicted > 0) {
                logger.info("Evicted " + evicted + " stale peers from tracker registry");
            }
        }, 10, 10, TimeUnit.SECONDS);

        logger.info("TrackerServer started on " + host + ":" + nioServer.getBoundPort());
    }

    public int getPort() {
        return nioServer != null ? nioServer.getBoundPort() : port;
    }

    public TrackerRegistry getRegistry() {
        return registry;
    }

    @Override
    public void onConnected(NioConnection connection) {
        // Connection opened
    }

    @Override
    public void onMessage(NioConnection connection, Message message) {
        try {
            if (message.getType() == MessageType.ANNOUNCE) {
                MessageCodec.AnnouncePayload announce = MessageCodec.decodeAnnounce(message.getPayload());
                String remoteIp = ((InetSocketAddress) connection.getChannel().getRemoteAddress()).getAddress().getHostAddress();

                // If announce has a reported port, use it
                registry.announce(announce.peerId(), announce.fileId(), remoteIp, announce.port(), announce.event(), announce.bitfield());

                // Respond with list of other peers for this file
                List<PeerInfo> peers = registry.getPeers(announce.fileId(), announce.peerId());
                Message response = MessageCodec.createPeerList(message.getRequestId(), peers);
                connection.send(response);
            } else if (message.getType() == MessageType.HEARTBEAT) {
                String peerId = MessageCodec.decodeHeartbeat(message.getPayload());
                registry.updateHeartbeat(peerId);
            }
        } catch (Exception e) {
            logger.warning("Error processing tracker message: " + e.getMessage());
            connection.send(MessageCodec.createError(message.getRequestId(), 500, e.getMessage()));
        }
    }

    @Override
    public void onDisconnected(NioConnection connection, Exception reason) {
        // Client disconnected
    }

    @Override
    public synchronized void close() {
        scheduler.shutdownNow();
        if (nioServer != null) {
            nioServer.close();
        }
        logger.info("TrackerServer stopped.");
    }
}
