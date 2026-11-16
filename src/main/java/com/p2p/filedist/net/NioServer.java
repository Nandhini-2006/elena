package com.p2p.filedist.net;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class NioServer implements Runnable, Closeable {
    private final String host;
    private final int port;
    private final MessageHandler handler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocketChannel serverChannel;
    private Selector selector;
    private Thread serverThread;
    private int boundPort;

    public NioServer(String host, int port, MessageHandler handler) {
        this.host = host;
        this.port = port;
        this.handler = handler;
    }

    public synchronized void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            this.selector = Selector.open();
            this.serverChannel = ServerSocketChannel.open();
            this.serverChannel.configureBlocking(false);
            this.serverChannel.bind(new InetSocketAddress(host, port));
            this.boundPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
            this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            this.serverThread = new Thread(this, "NioServer-" + boundPort);
            this.serverThread.setDaemon(true);
            this.serverThread.start();
        }
    }

    public int getBoundPort() {
        return boundPort;
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                int selected = selector.select(500);
                if (selected == 0) {
                    continue;
                }

                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        }
                        if (key.isValid() && key.isReadable()) {
                            handleRead(key);
                        }
                        if (key.isValid() && key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (Exception e) {
                        closeKey(key, e);
                    }
                }
            } catch (ClosedSelectorException e) {
                break;
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = ssc.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.socket().setTcpNoDelay(true);

            NioConnection connection = new NioConnection(clientChannel);
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ, connection);
            connection.setSelectionKey(clientKey);

            handler.onConnected(connection);
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        NioConnection connection = (NioConnection) key.attachment();
        List<com.p2p.filedist.protocol.Message> messages = connection.readAvailable();
        for (com.p2p.filedist.protocol.Message msg : messages) {
            handler.onMessage(connection, msg);
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        NioConnection connection = (NioConnection) key.attachment();
        connection.flushWrites();
    }

    private void closeKey(SelectionKey key, Exception reason) {
        NioConnection connection = (NioConnection) key.attachment();
        if (connection != null) {
            handler.onDisconnected(connection, reason);
            connection.close();
        } else {
            try {
                key.channel().close();
            } catch (IOException ignored) {}
            key.cancel();
        }
    }

    @Override
    public synchronized void close() {
        if (running.compareAndSet(true, false)) {
            if (selector != null) {
                try {
                    for (SelectionKey key : selector.keys()) {
                        try {
                            if (key.attachment() instanceof NioConnection conn) {
                                conn.close();
                            } else {
                                key.channel().close();
                            }
                        } catch (Exception ignored) {}
                    }
                    selector.close();
                } catch (IOException ignored) {}
            }
            if (serverChannel != null) {
                try {
                    serverChannel.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
