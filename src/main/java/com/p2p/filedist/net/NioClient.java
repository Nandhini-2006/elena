package com.p2p.filedist.net;

import com.p2p.filedist.common.Constants;
import com.p2p.filedist.protocol.Message;
import com.p2p.filedist.protocol.MessageHeader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class NioClient {

    public static SocketChannel openChannel(String host, int port) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(true); // blocking mode for direct request-response client tasks
        channel.socket().setTcpNoDelay(true);
        channel.socket().setSoTimeout((int) Constants.PIECE_REQUEST_TIMEOUT_MS);
        channel.connect(new InetSocketAddress(host, port));
        return channel;
    }

    public static Message sendAndReceive(String host, int port, Message request, long timeoutMs) throws IOException {
        try (SocketChannel channel = openChannel(host, port)) {
            channel.socket().setSoTimeout((int) timeoutMs);

            // Send request
            ByteBuffer outBuf = request.toByteBuffer();
            while (outBuf.hasRemaining()) {
                channel.write(outBuf);
            }

            // Read response header
            ByteBuffer headerBuf = ByteBuffer.allocate(Constants.HEADER_SIZE);
            while (headerBuf.hasRemaining()) {
                int read = channel.read(headerBuf);
                if (read == -1) {
                    throw new IOException("Premature EOF while reading message header");
                }
            }
            headerBuf.flip();
            MessageHeader header = MessageHeader.readFrom(headerBuf);

            // Read response payload
            byte[] payload = new byte[header.getPayloadLength()];
            if (payload.length > 0) {
                ByteBuffer payloadBuf = ByteBuffer.wrap(payload);
                while (payloadBuf.hasRemaining()) {
                    int read = channel.read(payloadBuf);
                    if (read == -1) {
                        throw new IOException("Premature EOF while reading message payload");
                    }
                }
            }

            return new Message(header, payload);
        }
    }
}
