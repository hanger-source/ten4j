package com.tenframework.core.connection;

import com.tenframework.core.message.ConnectionMigrationState;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.protocol.Protocol;
import com.tenframework.core.runloop.Runloop;
import io.netty.channel.Channel; // 保持导入，尽管不直接存储 Channel 字段
import lombok.extern.slf4j.Slf4j;

import java.net.SocketAddress; // 新增导入
import java.util.UUID;
import java.util.concurrent.CompletableFuture; // 新增导入
import com.tenframework.core.app.MessageReceiver; // 修正导入路径

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

    protected MessageReceiver messageReceiver; // 消息接收器，用于处理入站消息

    // 构造函数重构
    protected AbstractConnection(String connectionId, SocketAddress remoteAddress, Runloop initialRunloop) {
        this.connectionId = connectionId;
        this.remoteAddress = remoteAddress;
        this.currentRunloop = initialRunloop;
        this.migrationState = ConnectionMigrationState.INITIAL; // 初始状态
        log.info("AbstractConnection {}: 新建连接 from {}", connectionId, remoteAddress);
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    // 抽象方法，由具体实现类提供底层 Channel
    @Override
    public abstract Channel getChannel();

    @Override
    public Runloop getCurrentRunloop() {
        return currentRunloop;
    }

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

    // 移除 getEngine(), setRemoteLocation(), getProtocol(), setProtocol() 等方法，
    // 这些现在由 Connection 的使用者（如 App 或 Engine）管理，或由具体 Connection 实现来处理。

    @Override
    public void onMessageReceived(Message message) {
        // 消息接收逻辑。如果当前 Runloop 存在，将消息提交到该 Runloop 进行处理。
        if (currentRunloop != null) {
            log.debug("Connection {}: 接收到消息，提交到当前 Runloop: type={}, id={}", connectionId, message.getType(),
                    message.getId());
            currentRunloop.postTask(() -> {
                // 在 Runloop 线程中，将消息传递给依附的 MessageReceiver
                if (messageReceiver != null) {
                    messageReceiver.handleInboundMessage(message, this);
                } else {
                    log.warn("Connection {}: 消息 {} 没有注册的 MessageReceiver，消息被丢弃。", connectionId, message.getId());
                }
            });
        } else {
            log.warn("Connection {}: 接收到消息但没有关联的 Runloop，消息将被丢弃: type={}, id={}", connectionId, message.getType(),
                    message.getId());
        }
    }

    // 抽象方法，由具体实现类发送出站消息
    protected abstract CompletableFuture<Void> sendOutboundMessageInternal(Message message);

    @Override
    public CompletableFuture<Void> sendOutboundMessage(Message message) {
        // 检查连接是否活跃再发送
        if (getChannel() != null && getChannel().isActive()) {
            log.debug("Connection {}: 发送消息到远程客户端: type={}, id={}", connectionId, message.getType(), message.getId());
            return sendOutboundMessageInternal(message);
        } else {
            log.warn("Connection {}: 连接不活跃，无法发送消息: type={}, id={}", connectionId, message.getType(), message.getId());
            return CompletableFuture.failedFuture(new IllegalStateException("Connection is not active."));
        }
    }

    @Override
    public void close() {
        log.info("Connection {}: 正在关闭...", connectionId);
        cleanup(); // 执行清理逻辑
        // 实际的 Channel 关闭由 NettyConnection 或其 Netty 管道中的处理器负责
        this.migrationState = ConnectionMigrationState.CLOSED;
        log.info("Connection {}: 已关闭，当前状态: {}", connectionId, migrationState);
    }

    @Override
    public void migrate(Runloop targetRunloop, Location destinationLocation) { // 修改方法签名，接受 Runloop 和 Location
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
            // if (protocol != null) {
            // protocol.cleanup(); // 清理协议资源
            // }
            // TODO: 清理其他资源，如解除与 Engine 的绑定等
            // Channel 关闭将在 NettyConnection 或 NettyConnectionHandler 中处理
            this.migrationState = ConnectionMigrationState.CLEANED;
            log.info("Connection {}: 资源清理完成，当前状态: {}", connectionId, migrationState);
            // onProtocolCleaned(); // 移除，协议清理现在由 NettyConnectionHandler 隐式处理
        }
    }

    // 移除 onProtocolMigrated() 和 onProtocolCleaned()，因为协议处理现在封装在 Netty 层。
    /*
     * @Override
     * public void onProtocolMigrated() {
     * log.info("Connection {}: 协议层已迁移", connectionId);
     * }
     *
     * @Override
     * public void onProtocolCleaned() {
     * log.info("Connection {}: 协议层已清理", connectionId);
     * }
     */

    // 新增：设置消息接收器
    @Override
    public void setMessageReceiver(MessageReceiver messageReceiver) {
        this.messageReceiver = messageReceiver;
    }

    // 新增：获取消息接收器
    public MessageReceiver getMessageReceiver() {
        return messageReceiver;
    }
}