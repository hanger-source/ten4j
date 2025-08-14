package source.hanger.server.handler.decoder;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.ExtensionTypeHeader;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.msgpack.value.ValueType;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageConstants;

@Slf4j
public class MessagePackDecoder extends ByteToMessageDecoder {

    private final ObjectMapper objectMapper;

    public MessagePackDecoder() {
        objectMapper = new ObjectMapper(new MessagePackFactory());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 确保有足够的数据来读取至少一个 MsgPack 头部 (最少 1 字节)
        if (!in.isReadable()) {
            return;
        }

        // 读取所有可读字节到一个 byte 数组中
        int readableBytes = in.readableBytes();
        if (readableBytes == 0) {
            return; // 没有可读数据
        }
        byte[] bytes = new byte[readableBytes];
        in.readBytes(bytes); // 这会更新 ByteBuf 的 readerIndex

        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(bytes)) {
            if (!unpacker.hasNext()) { // 如果没有下一个 MsgPack 元素，说明数据不完整或已读完
                // 如果 byte[] 中没有足够数据，这里会返回 false
                return;
            }

            MessageFormat format = unpacker.getNextFormat();

            if (format.getValueType() == ValueType.EXTENSION) {
                ExtensionTypeHeader extHeader = unpacker.unpackExtensionTypeHeader();

                // 检查 EXT 负载的类型码
                if (extHeader.getType() == MessageConstants.TEN_MSGPACK_EXT_TYPE_MSG) {
                    // 第一阶段解码: 提取 EXT 负载 (内部消息体)
                    byte[] internalMsgBytes = new byte[extHeader.getLength()];
                    unpacker.readPayload(internalMsgBytes); // 读取 EXT 的负载

                    // 第二阶段解码: 将内部消息体反序列化为 Message 对象
                    Message message = objectMapper.readValue(internalMsgBytes, Message.class);
                    out.add(message);
                    log.debug("MsgPackDecoder: 解码 EXT 消息成功，类型: {}", message.getType());
                } else {
                    log.warn("MsgPackDecoder: 收到未知 EXT 类型消息: {}", extHeader.getType());
                    unpacker.skipValue(); // 跳过未知 EXT 负载
                }
            } else {
                log.warn("MsgPackDecoder: 收到非 EXT 类型消息，不进行处理。格式: {}", format);
                unpacker.skipValue(); // 跳过这个未知值
            }
        } catch (IOException e) {
            // 如果是 IOException，很可能是数据不完整或格式错误
            log.error("MsgPackDecoder: 解码消息 I/O 失败或数据不完整。", e);
            throw e; // 确保异常被传播，以便 Netty 能够处理连接关闭
        } catch (Exception e) {
            // 其他运行时异常，直接抛出
            log.error("MsgPackDecoder: 解码消息失败。", e);
            throw e;
        }
    }
}