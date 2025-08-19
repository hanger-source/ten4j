package source.hanger.core.connection;

import java.net.SocketAddress;

import io.netty.channel.Channel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.app.App;
import source.hanger.core.app.MessageReceiver;
import source.hanger.core.engine.Engine;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.ConnectionMigrationState;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message;
import source.hanger.core.message.command.Command;
import source.hanger.core.remote.Remote;
import source.hanger.core.runloop.Runloop;
import source.hanger.core.tenenv.RunloopFuture;
import source.hanger.core.tenenv.DefaultRunloopFuture;

/**
 * AbstractConnection 提供了 Connection 接口的骨架实现，
 * 对齐C/Python中的ten_connection_t结构体。
 */
@Slf4j
public abstract class AbstractConnection implements Connection {

    protected final String connectionId;
    protected SocketAddress remoteAddress; // 新增：远程地址
    protected Runloop currentRunloop; // 连接当前依附的 Runloop
    protected Location remoteLocation; // 新增：远程 Location
    protected ConnectionMigrationState migrationState; // 追踪连接迁移状态
    protected String uri; // 新增：连接的 URI，对齐 C 语言的 ten_connection_t.uri

    // 新增：获取消息接收器
    @Getter
    protected volatile MessageReceiver messageReceiver; // 添加 volatile 关键字
    protected volatile ConnectionAttachTo attachToState; // 新增：连接依附目标状态

    // 构造函数重构
    protected AbstractConnection(String connectionId, SocketAddress remoteAddress, Runloop initialRunloop) {
        this.connectionId = connectionId;
        this.remoteAddress = remoteAddress;
        this.currentRunloop = initialRunloop;
        this.migrationState = ConnectionMigrationState.INITIAL; // 初始状态
        this.attachToState = ConnectionAttachTo.INVALID; // 默认初始状态为 INVALID，对齐 C 语言
        this.uri = "connection://%s".formatted(connectionId); // 初始时将 connectionId 作为 uri
        log.info("AbstractConnection {}: 新建连接 from {}", connectionId, remoteAddress);
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    // 新增：获取连接的 URI
    @Override
    public String getUri() {
        return uri;
    }

    // 新增：设置连接的 URI
    @Override
    public void setUri(String uri) {
        this.uri = uri;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    // 抽象方法，由具体实现类提供底层 Channel
    @Override
    public abstract Channel getChannel();

    @Override
    public Location getRemoteLocation() {
        return remoteLocation;
    }

    @Override
    public void setRemoteLocation(Location remoteLocation) {
        this.remoteLocation = remoteLocation;
    }

    @Override
    public ConnectionMigrationState getMigrationState() {
        return migrationState;
    }

    @Override
    public void setMigrationState(ConnectionMigrationState migrationState) {
        this.migrationState = migrationState;
    }

    @Override
    public void onMessageReceived(Message message) {
        // 对齐 C 语言中 ten_connection_on_input 的逻辑：如果 connection 的 URI 为空，则从消息中设置
        if (message.getSrcLoc() != null && message.getSrcLoc().getAppUri() != null
                && !message.getSrcLoc().getAppUri().isEmpty() && (this.uri == null || this.uri.isEmpty())) {
            this.setUri(message.getSrcLoc().getAppUri());
            log.info("Connection {}: 根据入站消息设置 Connection URI 为 {}", connectionId, this.uri);
        }

        // C 端 ten_connection_on_msgs 中的逻辑：对于非命令消息，如果连接未依附于 Remote，则直接丢弃
        // 注意：C 端是在 ten_connection_on_msgs 内部循环处理每个消息时进行此判断，
        // 而这里 onMessageReceived 已经是一个消息的入口，所以直接在此处判断即可。
        if (!(message instanceof Command) && !(message instanceof CommandResult)) { // 检查是否是非命令消息
            if (this.attachToState != ConnectionAttachTo.REMOTE) {
                log.warn("Connection {}: 接收到非命令消息 {} (Type: {}) 但未依附于 Remote，消息被丢弃",
                        connectionId, message.getId(), message.getType());
                return; // 直接丢弃消息
            }
        }

        // 消息接收逻辑。如果当前 Runloop 存在，将消息提交到该 Runloop 进行处理。
        if (currentRunloop != null) {
            log.debug("Connection {}: 接收到消息，提交到当前 Runloop: type={}, id={}", connectionId, message.getType(),
                    message.getId());
            currentRunloop.postTask(() -> {
                // 在 Runloop 线程中，将消息传递给依附的 MessageReceiver
                if (messageReceiver != null) {
                    messageReceiver.handleInboundMessage(message, this);
                } else {
                    log.warn("Connection {}: 消息 {} 没有注册的 MessageReceiver，消息被丢弃。", connectionId,
                            message.getId());
                }
            });
        } else {
            log.warn("Connection {}: 接收到消息但没有关联的 Runloop，消息将被丢弃: type={}, id={}", connectionId,
                    message.getType(),
                    message.getId());
        }
    }

    // 抽象方法，由具体实现类发送出站消息
    protected abstract RunloopFuture<Void> sendOutboundMessageInternal(Message message);

    @Override
    public RunloopFuture<Void> sendOutboundMessage(Message message) {
        // 检查连接是否活跃再发送
        if (getChannel() != null && getChannel().isActive()) {
            log.debug("Connection {}: 发送消息到远程客户端: type={}, id={}", connectionId, message.getType(),
                    message.getId());
            return sendOutboundMessageInternal(message);
        } else {
            log.warn("Connection {}: 连接不活跃，无法发送消息: type={}, id={}", connectionId, message.getType(),
                    message.getId());
            return DefaultRunloopFuture.failedFuture(new IllegalStateException("Connection is not active."), currentRunloop);
        }
    }

    @Override
    public void close() {
        log.info("Connection {}: 正在关闭...", connectionId);
        cleanup(); // 执行清理逻辑
        // 实际的 Channel 关闭由 NettyConnection 或其 Netty 管道中的处理器负责
        this.migrationState = ConnectionMigrationState.CLOSED;
        log.info("Connection {}: 已关闭，当前状态: {}", connectionId, migrationState);

        // 通知依附的 Remote 连接已关闭
        if (messageReceiver instanceof Remote remote) {
            remote.onConnectionClosed(this);
            log.info("Connection {}: 已通知关联的 Remote {} 连接关闭事件。", connectionId, remote.getUri());
        }
    }

    @Override
    public void migrate(Runloop targetRunloop, Location destinationLocation) {
        // 实现 C 语言中 ten_connection_migrate 的逻辑
        // 将 Connection 的所有权和后续处理任务提交到 targetExecutor
        if (migrationState == ConnectionMigrationState.FIRST_MSG
                || migrationState == ConnectionMigrationState.INITIAL) {
            this.migrationState = ConnectionMigrationState.MIGRATING;
            this.currentRunloop = targetRunloop; // 更新当前 Runloop
            this.remoteLocation = destinationLocation; // 更新目标 Location

            // 提交一个任务到目标 Runloop，以完成迁移的后续步骤
            targetRunloop.postTask(() -> {
                try {
                    // protocol.handshake(); // 协议层进行迁移握手 (如果需要，现在由 NettyConnectionHandler 隐式处理)
                    onMigrated(); // 通知 Connection 迁移完成
                } catch (Exception e) {
                    log.error("Connection {}: 迁移失败: {}", connectionId, e.getMessage());
                    // TODO: 错误处理和回滚
                }
            });
        } else {
            log.warn("Connection {}: 无法迁移，当前状态为 {}", connectionId, migrationState);
        }
    }

    @Override
    public void onMigrated() {
        // 迁移完成后的回调，更新状态
        this.migrationState = ConnectionMigrationState.MIGRATED;
        log.info("Connection {}: 迁移到新线程完成，当前状态: {}", connectionId, migrationState);
        // onProtocolMigrated(); // 移除，协议迁移现在由 NettyConnectionHandler 隐式处理
    }

    @Override
    public void cleanup() {
        // 清理 Connection 相关的资源
        if (migrationState != ConnectionMigrationState.CLEANED) {
            this.migrationState = ConnectionMigrationState.CLEANING;
            log.info("Connection {}: 开始清理资源...", connectionId);
            // TODO: 清理其他资源，如解除与 Engine 的绑定等
            this.migrationState = ConnectionMigrationState.CLEANED;
            log.info("Connection {}: 资源清理完成，当前状态: {}", connectionId, migrationState);
        }
    }

    // 新增：设置消息接收器
    @Override
    public void setMessageReceiver(MessageReceiver messageReceiver) {
        this.messageReceiver = messageReceiver;
    }

    @Override
    public void setRunloop(Runloop runloop) {
        this.currentRunloop = runloop;
    }

    // 新增：依附于 App
    @Override
    public void attachToApp(App app) {
        this.attachToState = ConnectionAttachTo.APP;
        this.currentRunloop = app.getRunloop(); // App 通常有自己的 Runloop
        this.messageReceiver = app;
        log.info("Connection {}: 已依附于 App。", connectionId);
    }

    // 新增：依附于 Engine
    public void attachToEngine(Engine engine) {
        this.attachToState = ConnectionAttachTo.ENGINE;
        this.currentRunloop = engine.getRunloop(); // Connection 依附于 Engine 的 Runloop
        this.messageReceiver = engine; // Engine 将接收 Connection 的入站消息
        log.info("Connection {}: 已依附于 Engine {}{}", connectionId, engine.getGraphId(),
                engine.getRunloop().getCoreThread() != null
                        ? "，Runloop: " + engine.getRunloop().getCoreThread().getName()
                        : "");
    }

    // 新增：依附于 Remote （而不是直接 Engine）
    public void attachToRemote(Remote remote) { // 参数改为 Remote
        this.attachToState = ConnectionAttachTo.REMOTE;
        this.currentRunloop = remote.getRunloop(); // Connection 依附于 Remote 的 Runloop
        this.messageReceiver = remote; // Remote 将接收 Connection 的入站消息
        log.info("AbstractConnection {}: 依附到 Remote {}{}", connectionId, remote.getUri(),
                remote.getRunloop().getCoreThread() != null
                        ? "，Runloop: %s".formatted(remote.getRunloop().getCoreThread().getName())
                        : "");

        // 设置当前 Runloop 为 Remote 的 Runloop
        setRunloop(remote.getRunloop());
    }

    @Override
    public ConnectionAttachTo getAttachToState() {
        return attachToState;
    }
}