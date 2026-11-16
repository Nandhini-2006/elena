package com.p2p.filedist.protocol;

public enum MessageType {
    HANDSHAKE((byte) 1),
    ANNOUNCE((byte) 2),
    PEER_LIST((byte) 3),
    HAVE_PIECE((byte) 4),
    REQUEST_PIECE((byte) 5),
    PIECE_DATA((byte) 6),
    HEARTBEAT((byte) 7),
    BITFIELD((byte) 8),
    ERROR((byte) 9);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static MessageType fromCode(byte code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MessageType code: " + code);
    }
}
