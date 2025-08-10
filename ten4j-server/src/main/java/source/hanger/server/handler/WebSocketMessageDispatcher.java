package source.hanger.server.handler;

import java.util.HashMap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.message.Message;
import source.hanger.server.connection.NettyConnection;

/**
 * WebSocketMessageDispatcher 负责从 WebSocket Channel 读取 Message，并将其分发到 App。
 * 它处理消息的初步校验和路由，并将 Channel ID 作为消息属性传递。
 */
@Slf4j
public class WebSocketMessageDispatcher extends SimpleChannelInboundHandler<Message> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
        String channelId = ctx.channel().id().asShortText();
        // 修复：使用 getProperties().put() 来设置属性
        if (msg.getProperties() == null) {
            msg.setProperties(new HashMap<>()); // 确保 properties map 不为 null
        }
        NettyConnection connection = ctx.channel().attr(NettyConnection.CONNECTION_ATTRIBUTE_KEY).get();
        if (connection == null) {
            log.error("WebSocketMessageDispatcher: Channel {} 没有关联的 NettyConnection。", channelId);
            return;
        }

        // 修复：setSourceLocation 改为 setSrcLoc
        msg.setSrcLoc(connection.getRemoteLocation());

        // 将消息分发给 Connection 处理，而不是直接给 App
        connection.onMessageReceived(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // WebSocketServerProtocolHandler 会在握手成功时触发 HandshakeComplete
        // 这里可以进行一些连接建立后的初始化操作
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocketMessageDispatcher: Channel {} 发生异常: {}", ctx.channel().id().asShortText(),
            cause.getMessage(), cause);
        ctx.close(); // 关闭 Channel
    }
}