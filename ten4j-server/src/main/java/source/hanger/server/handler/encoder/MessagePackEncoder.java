package source.hanger.server.handler.encoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageConstants;

@Slf4j
public class MessagePackEncoder extends MessageToByteEncoder<Message> {

    private final ObjectMapper objectMapper;

    public MessagePackEncoder() {
        objectMapper = new ObjectMapper(new MessagePackFactory());
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        try {
            // 第一阶段编码: 将 Message 对象序列化为 MsgPack 字节数组 (内部消息体)
            byte[] internalMsgBytes = objectMapper.writeValueAsBytes(msg);

            // 第二阶段编码: 将内部消息体封装在自定义的 MsgPack EXT 类型中
            try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
                packer.packExtensionTypeHeader(MessageConstants.TEN_MSGPACK_EXT_TYPE_MSG, internalMsgBytes.length);
                packer.writePayload(internalMsgBytes);

                // 将最终的 EXT 字节写入 ByteBuf
                out.writeBytes(packer.toByteArray());
                log.debug("MsgPackEncoder: 编码消息成功，类型: {}，封装为 EXT 类型: {}", msg.getType(),
                    MessageConstants.TEN_MSGPACK_EXT_TYPE_MSG);
            }
        } catch (Exception e) {
            log.error("MsgPackEncoder: 编码消息失败。", e);
            throw e;
        }
    }
}