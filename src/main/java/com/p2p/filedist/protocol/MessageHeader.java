package com.p2p.filedist.protocol;

import com.p2p.filedist.common.Constants;
import java.nio.ByteBuffer;

public class MessageHeader {
    private final int magic;
    private final short version;
    private final MessageType type;
    private final int payloadLength;
    private final long requestId;

    public MessageHeader(MessageType type, int payloadLength, long requestId) {
        this(Constants.PROTOCOL_MAGIC, Constants.PROTOCOL_VERSION, type, payloadLength, requestId);
    }

    public MessageHeader(int magic, short version, MessageType type, int payloadLength, long requestId) {
        this.magic = magic;
        this.version = version;
        this.type = type;
        this.payloadLength = payloadLength;
        this.requestId = requestId;
    }

    public int getMagic() {
        return magic;
    }

    public short getVersion() {
        return version;
    }

    public MessageType getType() {
        return type;
    }

    public int getPayloadLength() {
        return payloadLength;
    }

    public long getRequestId() {
        return requestId;
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.putInt(magic);
        buffer.putShort(version);
        buffer.put(type.getCode());
        buffer.putInt(payloadLength);
        buffer.putLong(requestId);
    }

    public static MessageHeader readFrom(ByteBuffer buffer) {
        int magic = buffer.getInt();
        if (magic != Constants.PROTOCOL_MAGIC) {
            throw new IllegalArgumentException(String.format("Invalid protocol magic: 0x%X (expected 0x%X)", magic, Constants.PROTOCOL_MAGIC));
        }
        short version = buffer.getShort();
        byte typeCode = buffer.get();
        MessageType type = MessageType.fromCode(typeCode);
        int payloadLength = buffer.getInt();
        if (payloadLength < 0 || payloadLength > Constants.MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Payload length out of bounds: " + payloadLength);
        }
        long requestId = buffer.getLong();
        return new MessageHeader(magic, version, type, payloadLength, requestId);
    }

    @Override
    public String toString() {
        return "MessageHeader{" +
                "type=" + type +
                ", payloadLen=" + payloadLength +
                ", reqId=" + requestId +
                '}';
    }
}
