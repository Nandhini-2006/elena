package com.p2p.filedist.protocol;

import com.p2p.filedist.common.Constants;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public class Message {
    private static final AtomicLong REQUEST_ID_GEN = new AtomicLong(1);

    private final MessageHeader header;
    private final byte[] payload;

    public Message(MessageType type, byte[] payload) {
        this(type, REQUEST_ID_GEN.getAndIncrement(), payload);
    }

    public Message(MessageType type, long requestId, byte[] payload) {
        this.payload = payload != null ? payload : new byte[0];
        this.header = new MessageHeader(type, this.payload.length, requestId);
    }

    public Message(MessageHeader header, byte[] payload) {
        this.header = header;
        this.payload = payload != null ? payload : new byte[0];
    }

    public MessageHeader getHeader() {
        return header;
    }

    public MessageType getType() {
        return header.getType();
    }

    public long getRequestId() {
        return header.getRequestId();
    }

    public byte[] getPayload() {
        return payload;
    }

    public ByteBuffer toByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(Constants.HEADER_SIZE + payload.length);
        header.writeTo(buffer);
        if (payload.length > 0) {
            buffer.put(payload);
        }
        buffer.flip();
        return buffer;
    }

    @Override
    public String toString() {
        return "Message{" + header.getType() + ", reqId=" + header.getRequestId() + ", len=" + payload.length + "}";
    }
}
