package source.hanger.server.handler;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ByteBufToWebSocketFrameEncoder extends MessageToMessageEncoder<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        out.add(new BinaryWebSocketFrame(msg.retain())); // Wrap the ByteBuf in a BinaryWebSocketFrame
        log.debug("ByteBufToWebSocketFrameEncoder: Encoded ByteBuf to BinaryWebSocketFrame, size: {}",
                msg.readableBytes());
    }
}