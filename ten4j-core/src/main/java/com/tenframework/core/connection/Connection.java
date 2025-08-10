package com.tenframework.core.connection;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

import com.tenframework.core.app.MessageReceiver;
import com.tenframework.core.message.ConnectionMigrationState;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.runloop.Runloop;
import io.netty.channel.Channel;

/**
 * Connection 接口定义了与外部客户端连接的核心行为。
 */
public interface Connection {
    String getConnectionId();

    Channel getChannel();

    // 移除 getEngine(), getProtocol()，因为这些现在由 Connection 的使用者或具体实现管理。
    // Engine getEngine();

    SocketAddress getRemoteAddress(); // 新增：获取远程地址

    Location getRemoteLocation();

    void setRemoteLocation(Location remoteLocation); // 新增：设置远程位置

    ConnectionMigrationState getMigrationState();

    void setMigrationState(ConnectionMigrationState migrationState); // 新增：设置迁移状态

    // 移除 Protocol getProtocol();

    Runloop getCurrentRunloop(); // 新增：获取当前 Connection 依附的 Runloop

    void onMessageReceived(Message message);

    CompletableFuture<Void> sendOutboundMessage(Message message);

    void close();

    // 移除 bindToEngine(Engine engine);

    void migrate(Runloop targetRunloop, Location destinationLocation); // 修改签名以接收 Location

    void onMigrated();

    void cleanup();

    void setMessageReceiver(MessageReceiver messageReceiver); // 新增：设置消息接收器

    // 移除 onProtocolMigrated() 和 onProtocolCleaned()，因为协议处理现在封装在 Netty 层。
    // void onProtocolMigrated();
    // void onProtocolCleaned();
}