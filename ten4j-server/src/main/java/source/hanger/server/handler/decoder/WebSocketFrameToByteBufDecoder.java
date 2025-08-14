package source.hanger.server.handler.decoder;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketFrameToByteBufDecoder extends MessageToMessageDecoder<WebSocketFrame> {

    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) {
        if (frame instanceof BinaryWebSocketFrame) {
            ByteBuf binaryData = frame.content();
            out.add(binaryData.retain()); // retain the ByteBuf for the next handler
            log.debug("WebSocketFrameToByteBufDecoder: Decoded BinaryWebSocketFrame to ByteBuf, size: {}",
                binaryData.readableBytes());
        } else if (frame instanceof TextWebSocketFrame) {
            // If you expect text frames and want to convert them to ByteBuf, handle here.
            // For now, we only process binary frames for MessagePack.
            log.warn(
                "WebSocketFrameToByteBufDecoder: Received TextWebSocketFrame, but expecting BinaryWebSocketFrame. "
                    + "Content: {}",
                ((TextWebSocketFrame)frame).text());
        } else {
            log.warn("WebSocketFrameToByteBufDecoder: Received unknown WebSocketFrame type: {}",
                frame.getClass().getName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocketFrameToByteBufDecoder encountered an exception", cause);
        ctx.close();
    }
}