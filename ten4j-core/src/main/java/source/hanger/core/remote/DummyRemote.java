package source.hanger.core.remote;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.connection.Connection;
import source.hanger.core.engine.Engine;
import source.hanger.core.message.Message;
import source.hanger.core.runloop.Runloop;

/**
 * DummyRemote 是 Remote 接口的一个简单实现，用于测试或占位。
 */
@Slf4j
public class DummyRemote extends Remote {

    // 构造函数调整以匹配新的 Remote 基类
    public DummyRemote(String uri, Connection connection, Engine engine, Runloop runloop) {
        super(uri, connection, engine, runloop); // 调用新的基类构造函数
        log.info("DummyRemote: 创建了 Remote 实例: uri={}, connectionId={}, engineId={}",
                uri, connection.getConnectionId(), engine.getGraphId());
    }

    @Override // 覆盖 Remote 中的方法
    public CompletableFuture<Void> sendOutboundMessage(Message message) { // 方法名改为 sendOutboundMessage，返回
                                                                          // CompletableFuture<Void>
        if (message == null) {
            log.warn("DummyRemote {}: 尝试发送空消息。", getUri());
            return CompletableFuture.completedFuture(null); // 返回一个已完成的 CompletableFuture
        }

        // 使用基类 Remote 中的 connection 字段来发送消息
        if (getConnection() == null || !getConnection().getChannel().isActive()) {
            log.warn("DummyRemote {}: 关联连接不活跃或已关闭，无法发送消息。", getUri());
            return CompletableFuture.completedFuture(null); // 返回一个已完成的 CompletableFuture
        }
        // 调用 Connection 的发送方法
        CompletableFuture<Void> sentFuture = getConnection().sendOutboundMessage(message);
        sentFuture.thenRun(() -> {
            log.debug("DummyRemote: 消息 {} 已通过关联的 Connection {} 发送。", message.getId(),
                    getConnection().getConnectionId());
        });
        return sentFuture;
    }

    @Override // 覆盖 Remote 中的方法
    public void shutdown() {
        log.info("DummyRemote {}: 关闭 Remote。", getUri());
        // 调用基类 Remote 的 shutdown 逻辑，它会处理 connection.close()
        super.shutdown();
    }

    @Override
    public boolean handleInboundMessage(Message message, Connection connection) {
        // Dummy implementation
        log.debug("DummyRemote {}: 接收到入站消息 {}", getUri(), message.getId());
        return true;
    }

    @Override
    public void onConnectionClosed(Connection connection) {
        log.info("DummyRemote {}: 关联连接 {} 已关闭。", getUri(), connection.getConnectionId());
    }
}