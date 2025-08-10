package source.hanger.core.remote;

import java.util.Optional;

import source.hanger.core.connection.Connection;
import source.hanger.core.engine.Engine;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message;
import lombok.extern.slf4j.Slf4j;

/**
 * DummyRemote 是 Remote 抽象类的一个简化实现，用于测试和占位。
 * 它不包含实际的网络通信逻辑。
 */
@Slf4j
public class DummyRemote extends Remote {

    // 简化：目前只关联一个 Connection，实际 Remote 可能管理多个 Connection
    private Optional<Connection> associatedConnection;

    public DummyRemote(String remoteId, Location remoteEngineLocation, Engine localEngine,
            Optional<Connection> initialConnection) {
        super(remoteId, remoteEngineLocation, localEngine);
        associatedConnection = initialConnection;
        log.info("DummyRemote: 创建了 Remote 实例: remoteId={}, remoteEngineLocation={}, localEngineId={}",
            remoteId, remoteEngineLocation, localEngine.getGraphId());
    }

    @Override
    public void activate() {
        setActive(true);
        log.debug("DummyRemote: 激活 Remote: {}", getRemoteId());
    }

    @Override
    public void sendMessage(Message message) {
        if (!isActive()) {
            log.warn("DummyRemote: Remote {} 不活跃，无法发送消息: {}", getRemoteId(), message.getId());
            return;
        }
        log.debug("DummyRemote {}: 准备发送消息 {} 到 {}.", getRemoteId(), getRemoteEngineLocation().toString());
        associatedConnection.ifPresent(conn -> {
            conn.sendOutboundMessage(message);
            log.debug("DummyRemote: 消息 {} 已通过关联的 Connection {} 发送。", message.getId(), conn.getConnectionId());
        });
    }

    @Override
    public void shutdown() {
        setActive(false);
        log.debug("DummyRemote: 关闭 Remote: {}", getRemoteId());
        // 清理关联的 Connection
        associatedConnection.ifPresent(Connection::close); // 调用 Connection 的关闭方法
        associatedConnection = Optional.empty();
    }

    public Optional<Connection> getAssociatedConnection() {
        return associatedConnection;
    }

    public void setAssociatedConnection(Connection connection) {
        associatedConnection = Optional.ofNullable(connection);
    }
}