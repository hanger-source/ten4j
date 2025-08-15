package source.hanger.core.remote;

import java.util.concurrent.CompletableFuture;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.app.MessageReceiver;
import source.hanger.core.connection.Connection;
import source.hanger.core.engine.Engine;
import source.hanger.core.message.Message;
import source.hanger.core.runloop.Runloop;

/**
 * Remote 代表 TEN Framework 中的一个远程实体（例如另一个 App 或客户端）。
 * 它封装了与该远程实体通信所需的 Connection，并作为 Engine 与 Connection 之间的桥梁。
 * 与 C 语言中的 ten_remote_t 结构体对齐。
 */
@Slf4j
public class Remote implements MessageReceiver {

    @Getter // 添加 Getter
    private final String uri;
    @Getter // 添加 Getter
    private final Connection connection;
    private final Engine engine;
    @Getter // 添加 Getter
    private final Runloop runloop;

    public Remote(String uri, Connection connection, Engine engine, Runloop runloop) {
        this.uri = uri;
        this.connection = connection;
        this.engine = engine;
        this.runloop = runloop;
        log.info("Remote {}: 创建，关联连接 {}，关联引擎 {}", uri, connection.getConnectionId(),
                engine.getGraphId());

        // 关键修改：在 Remote 构造时，就将 Connection 依附到自身
        this.connection.attachToRemote(this);
    }

    /**
     * 接收来自其关联 Connection 的入站消息，并提交给关联的 Engine。
     * 与 C 语言中 ten_remote_on_input 的逻辑对齐。
     *
     * @param message    接收到的消息。
     * @param connection 消息来源的 Connection。
     * @return 消息是否成功提交。
     */
    @Override // 覆盖 MessageReceiver 的方法
    public boolean handleInboundMessage(Message message, Connection connection) { // 方法名改为 handleInboundMessage
        if (message == null) {
            log.warn("Remote {}: 尝试提交空消息。", uri);
            return false;
        }
        // 确保消息源 URI 设置为 Remote 的 URI
        // message.setSrcLoc(message.getSrcLoc().toBuilder().appUri(remoteUri).build());

        // 确保消息的源 App URI 被设置为此 Remote 的 URI
        if (message.getSrcLoc() != null && (message.getSrcLoc().getAppUri() == null || message.getSrcLoc().getAppUri()
                .isEmpty())) {
            message.getSrcLoc().setAppUri(this.uri).setGraphId(engine.getGraphId());
            log.debug("Remote {}: 设置入站消息 {} 的源 App URI 为 {}", uri, message.getId(), this.uri);
        }

        // 将消息提交给关联的 Engine
        return engine.submitInboundMessage(message, this.connection); // 传递原始 Connection
    }

    /**
     * 从 Engine 接收出站消息，并通过其持有的 Connection 发送出去。
     * 与 C 语言中 ten_remote_send_msg 的逻辑对齐。
     *
     * @param message 要发送的消息。
     * @return 消息是否成功发送。
     */
    public CompletableFuture<Void> sendOutboundMessage(Message message) { // 修正返回类型为 CompletableFuture<Void>
        if (message == null) {
            log.warn("Remote {}: 尝试发送空消息。", uri);
            return CompletableFuture.completedFuture(null); // 返回一个已完成的 CompletableFuture
        }

        if (connection == null || !connection.getChannel().isActive()) {
            log.warn("Remote {}: 关联连接不活跃或已关闭，无法发送消息。", uri);
            return CompletableFuture.completedFuture(null); // 返回一个已完成的 CompletableFuture
        }

        // 对齐 C 语言中 ten_remote_send_msg 的逻辑：在发送前设置消息的源 URI 为 Remote 的 URI
        // 只有当消息的源 URI 未被指定时才设置，防止覆盖上层已设置的源
        if (message.getSrcLoc() != null && (message.getSrcLoc().getAppUri() == null || message.getSrcLoc().getAppUri()
                .isEmpty())) {
            message.getSrcLoc().setAppUri(this.uri).setGraphId(engine.getGraphId());
            log.debug("Remote {}: 设置出站消息 {} 的源 App URI 为 {}", uri, message.getId(), this.uri);
        }

        // 调用 Connection 的发送方法
        return connection.sendOutboundMessage(message);
    }

    /**
     * 关闭此 Remote 及其所有底层资源。
     * 与 C 语言中 ten_remote_close 的逻辑对齐。
     */
    public void shutdown() {
        log.info("Remote {}: 正在关闭...", uri);
        if (connection != null) {
            connection.close(); // 关闭关联的连接
        }
        // TODO: 其他资源清理，例如从 Engine 中移除自身
        if (engine != null) {
            engine.removeRemote(this.uri); // 通知 Engine 移除此 Remote
        }
        log.info("Remote {}: 已关闭。", uri);
    }

    /**
     * 当关联的 Connection 关闭时被调用。
     *
     * @param connection 已关闭的 Connection 实例。
     */
    public void onConnectionClosed(Connection connection) {
        log.info("Remote {}: 关联连接 {} 已关闭，通知关联 Engine 移除此 Remote。");
        // TODO: 通知 Engine 或 App 移除此 Remote
        // 例如：engine.removeRemote(this); // 如果 Engine 管理 Remote 列表
    }
}