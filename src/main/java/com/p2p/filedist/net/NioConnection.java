package com.p2p.filedist.net;

import com.p2p.filedist.common.Constants;
import com.p2p.filedist.protocol.Message;
import com.p2p.filedist.protocol.MessageHeader;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NioConnection implements Closeable {
    private final SocketChannel channel;
    private final ByteBuffer readBuffer;
    private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
    private SelectionKey selectionKey;
    private Object attachment;

    // Decoding state
    private MessageHeader currentHeader = null;
    private ByteBuffer payloadBuffer = null;

    public NioConnection(SocketChannel channel) {
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocate(Constants.SOCKET_BUFFER_SIZE);
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public void setAttachment(Object attachment) {
        this.attachment = attachment;
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttachment() {
        return (T) attachment;
    }

    public SocketAddress getRemoteAddress() {
        try {
            return channel.getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Reads available bytes from channel and parses any complete protocol Messages.
     */
    public List<Message> readAvailable() throws IOException {
        int bytesRead = channel.read(readBuffer);
        if (bytesRead == -1) {
            throw new IOException("Connection closed by peer");
        }

        List<Message> completedMessages = new ArrayList<>();
        readBuffer.flip();

        while (true) {
            if (currentHeader == null) {
                if (readBuffer.remaining() < Constants.HEADER_SIZE) {
                    // Not enough data for header yet
                    break;
                }
                currentHeader = MessageHeader.readFrom(readBuffer);
                if (currentHeader.getPayloadLength() == 0) {
                    completedMessages.add(new Message(currentHeader, new byte[0]));
                    currentHeader = null;
                    continue;
                } else {
                    payloadBuffer = ByteBuffer.allocate(currentHeader.getPayloadLength());
                }
            }

            if (currentHeader != null && payloadBuffer != null) {
                int toTransfer = Math.min(readBuffer.remaining(), payloadBuffer.remaining());
                if (toTransfer > 0) {
                    int oldLimit = readBuffer.limit();
                    readBuffer.limit(readBuffer.position() + toTransfer);
                    payloadBuffer.put(readBuffer);
                    readBuffer.limit(oldLimit);
                }

                if (!payloadBuffer.hasRemaining()) {
                    // Payload is complete
                    payloadBuffer.flip();
                    byte[] payloadBytes = new byte[payloadBuffer.remaining()];
                    payloadBuffer.get(payloadBytes);
                    completedMessages.add(new Message(currentHeader, payloadBytes));

                    currentHeader = null;
                    payloadBuffer = null;
                } else {
                    // Need more bytes for payload
                    break;
                }
            }
        }

        readBuffer.compact();
        return completedMessages;
    }

    /**
     * Enqueues a message to be written.
     */
    public synchronized void send(Message message) {
        ByteBuffer buf = message.toByteBuffer();
        writeQueue.offer(buf);
        if (selectionKey != null && selectionKey.isValid()) {
            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
            selectionKey.selector().wakeup();
        }
    }

    /**
     * Synchronously flushes message directly (useful in client/worker threads).
     */
    public synchronized void sendDirect(Message message) throws IOException {
        ByteBuffer buf = message.toByteBuffer();
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    /**
     * Called when OP_WRITE is ready.
     */
    public synchronized void flushWrites() throws IOException {
        while (!writeQueue.isEmpty()) {
            ByteBuffer head = writeQueue.peek();
            channel.write(head);
            if (head.hasRemaining()) {
                // Socket buffer full, wait for next OP_WRITE
                return;
            }
            writeQueue.poll();
        }

        // Nothing left to write, remove OP_WRITE interest
        if (selectionKey != null && selectionKey.isValid()) {
            selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }

    public boolean hasPendingWrites() {
        return !writeQueue.isEmpty();
    }

    @Override
    public void close() {
        try {
            if (selectionKey != null) {
                selectionKey.cancel();
            }
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ignored) {}
    }
}
