package com.p2p.filedist.model;

import java.io.*;
import java.util.Objects;

public class PeerInfo {
    private final String peerId;
    private final String ipAddress;
    private final int port;
    private long lastSeen;
    private byte[] bitfieldBytes;

    public PeerInfo(String peerId, String ipAddress, int port) {
        this.peerId = peerId;
        this.ipAddress = ipAddress;
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
        this.bitfieldBytes = new byte[0];
    }

    public PeerInfo(String peerId, String ipAddress, int port, byte[] bitfieldBytes) {
        this(peerId, ipAddress, port);
        this.bitfieldBytes = bitfieldBytes != null ? bitfieldBytes : new byte[0];
    }

    public String getPeerId() {
        return peerId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public synchronized long getLastSeen() {
        return lastSeen;
    }

    public synchronized void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    public synchronized byte[] getBitfieldBytes() {
        return bitfieldBytes;
    }

    public synchronized void setBitfieldBytes(byte[] bitfieldBytes) {
        this.bitfieldBytes = bitfieldBytes != null ? bitfieldBytes : new byte[0];
    }

    public void writeTo(DataOutputStream dos) throws IOException {
        dos.writeUTF(peerId);
        dos.writeUTF(ipAddress);
        dos.writeInt(port);
        dos.writeInt(bitfieldBytes.length);
        if (bitfieldBytes.length > 0) {
            dos.write(bitfieldBytes);
        }
    }

    public static PeerInfo readFrom(DataInputStream dis) throws IOException {
        String peerId = dis.readUTF();
        String ip = dis.readUTF();
        int port = dis.readInt();
        int bitfieldLen = dis.readInt();
        byte[] bitfield = new byte[bitfieldLen];
        if (bitfieldLen > 0) {
            dis.readFully(bitfield);
        }
        return new PeerInfo(peerId, ip, port, bitfield);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return port == peerInfo.port && Objects.equals(peerId, peerInfo.peerId) && Objects.equals(ipAddress, peerInfo.ipAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId, ipAddress, port);
    }

    @Override
    public String toString() {
        return "PeerInfo{" + peerId + "@" + ipAddress + ":" + port + "}";
    }
}
