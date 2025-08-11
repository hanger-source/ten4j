package source.hanger.server.handler;

import java.util.UUID;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.app.App;
import source.hanger.core.message.Location;
import source.hanger.server.connection.NettyConnection;

/**
 * NettyConnectionHandler 负责处理 Netty Channel 的生命周期事件。
 * 它在 Channel 活跃时创建 NettyConnection 实例，并将其传递给 App。
 */
@Slf4j
public class NettyConnectionHandler extends ChannelInboundHandlerAdapter {

    private final App app;

    public NettyConnectionHandler(App app) {
        this.app = app;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 当一个新的 Netty Channel 活跃时，创建对应的 NettyConnection 实例
        String connectionId = UUID.randomUUID().toString(); // 生成唯一 ID
        NettyConnection connection = new NettyConnection(
            connectionId,
            ctx.channel().remoteAddress(),
            ctx.channel(),
            app.getAppRunloop() // 将 App 的 Runloop 传递给 Connection
        );
        log.info("NettyConnectionHandler: Channel {} 活跃，创建新的 NettyConnection: {}",
            ctx.channel().id().asShortText(),
            connectionId);

        // 设置 Connection 的 remoteLocation。在连接建立初期，客户端的逻辑源 URI 是未知的。
        // 它将在接收到 StartGraphCommand 等命令后，根据命令中的 srcLoc 更新。
        // 此时，remoteLocation 仅作为 Location 对象的载体，其 appUri 和 graphId 暂时为空。
        // 物理地址已通过 connection.getRemoteAddress() 获取。
        connection.setRemoteLocation(new Location()); // 初始为空 Location

        // 将 NettyConnection 存储到 Channel 的属性中，以便后续可以在管道中获取
        ctx.channel().attr(NettyConnection.CONNECTION_ATTRIBUTE_KEY).set(connection);

        // 通知 App 有新的连接
        app.onNewConnection(connection);

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 当 Netty Channel 不活跃时，通知 App 对应的连接已断开，并进行清理
        NettyConnection connection = ctx.channel().attr(NettyConnection.CONNECTION_ATTRIBUTE_KEY).get();
        if (connection != null) {
            log.info("NettyConnectionHandler: Channel {} 不活跃，关闭 NettyConnection: {}",
                ctx.channel().id().asShortText(),
                connection.getConnectionId());
            connection.close(); // 触发 Connection 的清理逻辑
            app.onConnectionClosed(connection); // 通知 App 连接已关闭
            ctx.channel().attr(NettyConnection.CONNECTION_ATTRIBUTE_KEY).set(null); // 移除属性
        } else {
            log.warn("NettyConnectionHandler: Channel {} 不活跃，但未找到对应的 NettyConnection 实例。",
                ctx.channel().id().asShortText());
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("NettyConnectionHandler: Channel {} 发生异常: {}", ctx.channel().id().asShortText(),
            cause.getMessage(),
            cause);
        // 尝试关闭相关的 Connection，如果存在
        NettyConnection connection = ctx.channel().attr(NettyConnection.CONNECTION_ATTRIBUTE_KEY).get();
        if (connection != null) {
            connection.close();
            app.onConnectionClosed(connection); // 通知 App 连接已关闭
            ctx.channel().attr(NettyConnection.CONNECTION_ATTRIBUTE_KEY).set(null); // 移除属性
        }
        ctx.close(); // 关闭 Channel
    }
}