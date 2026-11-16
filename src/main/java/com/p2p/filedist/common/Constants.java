package com.p2p.filedist.common;

public final class Constants {
    private Constants() {}

    // Protocol Constants
    public static final int PROTOCOL_MAGIC = 0x50325046; // "P2PF"
    public static final short PROTOCOL_VERSION = 1;
    public static final int HEADER_SIZE = 4 + 2 + 1 + 4 + 8; // 19 bytes: magic(4) + ver(2) + type(1) + len(4) + reqId(8)
    public static final int MAX_PAYLOAD_SIZE = 16 * 1024 * 1024; // 16 MB max frame protection

    // Default Configuration
    public static final int DEFAULT_TRACKER_PORT = 6881;
    public static final int DEFAULT_PIECE_SIZE = 64 * 1024; // 64 KB pieces for fast responsive transfers
    public static final int SOCKET_BUFFER_SIZE = 128 * 1024; // 128 KB NIO buffer
    public static final long TRACKER_HEARTBEAT_TIMEOUT_MS = 45_000L;
    public static final long PIECE_REQUEST_TIMEOUT_MS = 5_000L;
}
