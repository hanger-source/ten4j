package source.hanger.server.connection;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.connection.AbstractConnection;
import source.hanger.core.message.Message;
import source.hanger.core.runloop.Runloop;

/**
 * NettyConnection 是 Connection 接口的实现，用于封装 Netty Channel。
 * 它处理消息的编解码和通过 Netty Channel 的发送。
 */
@Slf4j
public class NettyConnection extends AbstractConnection {

    // 用于在 Netty Channel 中存储 NettyConnection 实例的属性键
    public static final AttributeKey<NettyConnection> CONNECTION_ATTRIBUTE_KEY = AttributeKey
        .newInstance("NettyConnection");

    private final Channel channel;

    public NettyConnection(String connectionId, SocketAddress remoteAddress, Channel channel, Runloop initialRunloop) {
        super(connectionId, remoteAddress, initialRunloop); // 调用父类构造函数
        this.channel = channel;
        log.info("NettyConnection: {} 实例创建，绑定到 Channel {}", connectionId, channel.id().asShortText());
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    protected void sendOutboundMessageInternal(Message message) {
        if (channel.isActive()) {
            ChannelFuture writeFuture = channel.writeAndFlush(message);
            writeFuture.addListener(f -> {
                if (f.isSuccess()) {
                    log.debug("NettyConnection {}: 消息 {} (类型: {} name: {}) 发送成功。", getConnectionId(),
                        message.getId(),
                        message.getType(), message.getName());
                } else {
                    log.error("NettyConnection {}: 消息 {} (类型: {} name: {}) 发送失败: {}", getConnectionId(),
                        message.getId(),
                        message.getType(), message.getName(), f.cause().getMessage());
                }
            });
        } else {
            log.warn("NettyConnection {}: Channel 不活跃，消息 {} (类型: {} name: {}) 无法发送。", getConnectionId(),
                message.getId(),
                message.getType(),
                message.getName());
        }
    }

    @Override
    public void close() {
        if (channel.isOpen()) {
            log.info("NettyConnection {}: 关闭底层 Netty Channel {}", getConnectionId(), channel.id().asShortText());
            channel.close();
        }
        super.close();
    }
}