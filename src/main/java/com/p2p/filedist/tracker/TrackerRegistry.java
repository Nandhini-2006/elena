package com.p2p.filedist.tracker;

import com.p2p.filedist.model.PeerInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TrackerRegistry {
    // fileId -> (peerId -> PeerInfo)
    private final Map<String, Map<String, PeerInfo>> fileSwarmMap = new ConcurrentHashMap<>();
    // peerId -> lastSeen timestamp
    private final Map<String, Long> peerHeartbeatMap = new ConcurrentHashMap<>();

    public synchronized void announce(String peerId, String fileId, String ip, int port, String event, byte[] bitfield) {
        if ("STOPPED".equalsIgnoreCase(event)) {
            removePeerFromFile(fileId, peerId);
            return;
        }

        peerHeartbeatMap.put(peerId, System.currentTimeMillis());
        fileSwarmMap.computeIfAbsent(fileId, k -> new ConcurrentHashMap<>());

        PeerInfo peer = fileSwarmMap.get(fileId).computeIfAbsent(peerId, k -> new PeerInfo(peerId, ip, port, bitfield));
        peer.updateLastSeen();
        if (bitfield != null && bitfield.length > 0) {
            peer.setBitfieldBytes(bitfield);
        }
    }

    public synchronized void updateHeartbeat(String peerId) {
        peerHeartbeatMap.put(peerId, System.currentTimeMillis());
        for (Map<String, PeerInfo> swarm : fileSwarmMap.values()) {
            PeerInfo peer = swarm.get(peerId);
            if (peer != null) {
                peer.updateLastSeen();
            }
        }
    }

    public List<PeerInfo> getPeers(String fileId, String excludePeerId) {
        Map<String, PeerInfo> swarm = fileSwarmMap.get(fileId);
        if (swarm == null) {
            return Collections.emptyList();
        }
        return swarm.values().stream()
                .filter(p -> excludePeerId == null || !p.getPeerId().equals(excludePeerId))
                .collect(Collectors.toList());
    }

    public synchronized void removePeerFromFile(String fileId, String peerId) {
        Map<String, PeerInfo> swarm = fileSwarmMap.get(fileId);
        if (swarm != null) {
            swarm.remove(peerId);
            if (swarm.isEmpty()) {
                fileSwarmMap.remove(fileId);
            }
        }
    }

    public synchronized int evictStalePeers(long timeoutMs) {
        long now = System.currentTimeMillis();
        int evicted = 0;

        Iterator<Map.Entry<String, Long>> it = peerHeartbeatMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > timeoutMs) {
                String deadPeerId = entry.getKey();
                it.remove();
                for (Map<String, PeerInfo> swarm : fileSwarmMap.values()) {
                    if (swarm.remove(deadPeerId) != null) {
                        evicted++;
                    }
                }
            }
        }
        return evicted;
    }

    public Map<String, List<PeerInfo>> getAllSwarms() {
        Map<String, List<PeerInfo>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, PeerInfo>> e : fileSwarmMap.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue().values()));
        }
        return copy;
    }
}
