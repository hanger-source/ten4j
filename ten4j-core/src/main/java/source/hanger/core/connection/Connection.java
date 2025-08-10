package source.hanger.core.connection;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

import io.netty.channel.Channel;
import source.hanger.core.app.App;
import source.hanger.core.app.MessageReceiver;
import source.hanger.core.message.ConnectionMigrationState;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message;
import source.hanger.core.remote.Remote;
import source.hanger.core.runloop.Runloop;
import source.hanger.core.engine.Engine; // 新增：导入 Engine 类

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

    String getUri(); // 新增：获取连接的 URI

    void setUri(String uri); // 新增：设置连接的 URI

    ConnectionMigrationState getMigrationState();

    void setMigrationState(ConnectionMigrationState migrationState); // 新增：设置迁移状态

    // 移除 Protocol getProtocol();

    // 移除：获取当前 Connection 依附的 Runloop
    // Runloop getCurrentRunloop();

    void onMessageReceived(Message message);

    CompletableFuture<Void> sendOutboundMessage(Message message);

    void close();

    // 移除 bindToEngine(Engine engine);

    void migrate(Runloop targetRunloop, Location destinationLocation); // 修改签名以接收 Location

    void onMigrated();

    void cleanup();

    void setMessageReceiver(MessageReceiver messageReceiver); // 新增：设置消息接收器

    // 新增：依附于 App
    void attachToApp(App app);

    // 新增：依附于 Engine
    void attachToEngine(Engine engine);

    // 新增：依附于 Remote
    void attachToRemote(Remote remote);

    // 新增：设置 Runloop
    void setRunloop(Runloop runloop);

    // 新增：获取依附状态
    ConnectionAttachTo getAttachToState();
}