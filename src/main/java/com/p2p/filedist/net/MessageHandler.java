package com.p2p.filedist.net;

import com.p2p.filedist.protocol.Message;

public interface MessageHandler {
    void onConnected(NioConnection connection);
    void onMessage(NioConnection connection, Message message);
    void onDisconnected(NioConnection connection, Exception reason);
}
