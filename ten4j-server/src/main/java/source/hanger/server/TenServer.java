package source.hanger.server;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.app.App;
import source.hanger.server.handler.MessagePackDecoder;
import source.hanger.server.handler.MessagePackEncoder;
import source.hanger.server.handler.NettyConnectionHandler;
import source.hanger.server.handler.WebSocketMessageDispatcher;

/**
 * TenServer 类封装了 Netty 服务器的启动、停止和配置。
 * 它是服务器的核心组件，负责监听传入连接并设置消息处理管道。
 */
@Slf4j
public class TenServer {

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MILLIS = 500;

    private final int initialPort;
    private final App app; // 将 Engine 替换为 App
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;

    private int currentPort;

    public TenServer(int port, App app) { // 构造函数接收 App 实例
        initialPort = port;
        this.app = app;
        currentPort = port;
    }

    private static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法找到可用端口: %s".formatted(e.getMessage()), e);
        }
    }

    public CompletableFuture<Void> start() {
        CompletableFuture<Void> serverStartFuture = new CompletableFuture<>();
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                bossGroup = new NioEventLoopGroup(1); // 一个用于接受传入连接的线程
                workerGroup = new NioEventLoopGroup(); // 用于处理已接受连接的事件

                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO)) // 添加日志处理器
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(
                                new HttpServerCodec(), // HTTP 编解码器
                                new HttpObjectAggregator(65536), // HTTP 消息聚合器
                                new ChunkedWriteHandler(), // 处理大文件传输
                                // WebSocket 协议处理器，路径为 "/websocket"
                                // 在握手完成后，HTTP 请求会被替换为 WebSocket 帧
                                new WebSocketServerProtocolHandler("/websocket"),
                                new WebSocketFrameAggregator(8192), // 聚合 WebSocket 帧
                                // MsgPack 编解码器
                                new MessagePackDecoder(), // MsgPack 解码器
                                new MessagePackEncoder(), // MsgPack 编码器
                                new NettyConnectionHandler(app), // 负责 Connection 生命周期管理和消息转发给 App
                                // WebSocketMessageDispatcher 现在只处理核心消息分发
                                new WebSocketMessageDispatcher() // WebSocket 消息调度器，传入 App
                            );
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128) // TCP/IP 连接队列的最大长度
                    .childOption(ChannelOption.SO_KEEPALIVE, true); // 启用 TCP Keep-Alive

                channelFuture = b.bind(currentPort).sync(); // 同步绑定端口
                currentPort = ((InetSocketAddress)channelFuture.channel().localAddress()).getPort();
                log.info("TenServer successfully started on port {}", currentPort);
                serverStartFuture.complete(null);
                return serverStartFuture;

            } catch (Exception e) {
                if (e.getCause() instanceof BindException) {
                    log.warn("Port {} already in use on attempt {}/{}. Retrying with new port...",
                        currentPort, attempt + 1, MAX_RETRY_ATTEMPTS);
                    currentPort = findAvailablePort(); // 重新查找可用端口
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MILLIS);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        serverStartFuture.completeExceptionally(new IllegalStateException(
                            "Server startup interrupted during retry.", interruptedException));
                        return serverStartFuture;
                    }
                } else {
                    serverStartFuture.completeExceptionally(e);
                    return serverStartFuture;
                }
            }
        }
        serverStartFuture.completeExceptionally(new RuntimeException(
            "Failed to start TenServer after " + MAX_RETRY_ATTEMPTS + " attempts due to port binding issues."));
        return serverStartFuture;
    }

    public CompletableFuture<Void> shutdown() {
        log.info("TenServer shutting down.");
        CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();
        if (channelFuture != null) {
            channelFuture.channel().closeFuture().addListener(f -> {
                if (f.isSuccess()) {
                    // Channel 关闭后，关闭 EventLoopGroup
                    if (bossGroup != null) {
                        bossGroup.shutdownGracefully();
                    }
                    if (workerGroup != null) {
                        workerGroup.shutdownGracefully();
                    }
                    shutdownFuture.complete(null);
                } else {
                    log.error("Error during channel close: {}", f.cause().getMessage());
                    shutdownFuture.completeExceptionally(f.cause());
                }
            });
        } else {
            shutdownFuture.complete(null);
        }
        return shutdownFuture;
    }

    public int getPort() {
        return currentPort;
    }
}