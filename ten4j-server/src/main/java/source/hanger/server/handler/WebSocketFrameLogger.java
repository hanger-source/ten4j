package source.hanger.server.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocketFrameLogger 用于在 Netty 管道中记录 WebSocket 帧的流入和流出。
 * 它位于 WebSocketServerProtocolHandler 之后，可以捕获真实的 WebSocket 帧。
 */
@Slf4j
public class WebSocketFrameLogger extends ChannelDuplexHandler {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;
            String frameType = getFrameType(frame);
            log.debug("WebSocketFrameLogger: 收到 {} 帧 (rsv: {}, final: {}, payloadLength: {})",
                    frameType, frame.rsv(), frame.isFinalFragment(), frame.content().readableBytes());
            // 如果是文本帧，可以记录内容
            if (frame instanceof TextWebSocketFrame) {
                log.debug("WebSocketFrameLogger: Text Frame Content: {}", ((TextWebSocketFrame) frame).text());
            }
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;
            String frameType = getFrameType(frame);
            log.debug("WebSocketFrameLogger: 发送 {} 帧 (rsv: {}, final: {}, payloadLength: {})",
                    frameType, frame.rsv(), frame.isFinalFragment(), frame.content().readableBytes());
            // 如果是文本帧，可以记录内容
            if (frame instanceof TextWebSocketFrame) {
                log.debug("WebSocketFrameLogger: Text Frame Content: {}", ((TextWebSocketFrame) frame).text());
            }
        }
        super.write(ctx, msg, promise);
    }

    private String getFrameType(WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            return "TEXT";
        } else if (frame instanceof BinaryWebSocketFrame) {
            return "BINARY";
        } else if (frame instanceof PingWebSocketFrame) {
            return "PING";
        } else if (frame instanceof PongWebSocketFrame) {
            return "PONG";
        } else if (frame instanceof CloseWebSocketFrame) {
            return "CLOSE";
        } else {
            return "UNKNOWN";
        }
    }
}