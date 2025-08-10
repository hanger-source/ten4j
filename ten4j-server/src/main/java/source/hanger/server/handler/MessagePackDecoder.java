package source.hanger.server.handler;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageConstants;
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

@Slf4j
public class MessagePackDecoder extends ByteToMessageDecoder {

    private final ObjectMapper objectMapper;

    public MessagePackDecoder() {
        objectMapper = new ObjectMapper(new MessagePackFactory());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 标记读指针位置，以便在数据不足时回滚
        in.markReaderIndex();

        // 确保有足够的数据来读取至少一个 MsgPack 头部 (最少 1 字节)
        if (!in.isReadable()) {
            return;
        }

        // 复制 ByteBuf 到一个临时的 byte[] 供 MessageUnpacker 使用
        // 这种方式比较简单，但如果消息非常大，可能会有内存拷贝的开销。
        // 对于小消息，性能影响不大。
        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes); // 读取所有可读字节，此时 in 的 readerIndex 已经移动到末尾

        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(bytes)) {
            if (!unpacker.hasNext()) { // 如果没有下一个 MsgPack 元素，说明数据不完整或已读完
                in.resetReaderIndex(); // 回滚读指针，等待更多数据
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
                // 如果不是 EXT 类型，并且我们期望所有 ten-framework 消息都是 EXT 类型，这可能是一个错误
                // 但为了兼容性或调试，我们也可以尝试直接反序列化。
                // 在此情境下，我们期望所有 Ten 框架消息都通过 EXT 类型封装。
                // 如果收到非 EXT 类型，可能是其他数据或错误，此处不应尝试解码为 Message。
                // 而是直接跳过，或者抛出异常。
                log.warn("MsgPackDecoder: 收到非 EXT 类型消息，不进行处理。格式: {}", format);
                unpacker.skipValue(); // 跳过这个未知值
            }
        } catch (IOException e) {
            // 如果是 IOException，很可能是数据不完整或格式错误
            // 此时回滚读指针，等待更多数据或者让上游/后续处理器处理
            in.resetReaderIndex();
            log.error("MsgPackDecoder: 解码消息 I/O 失败或数据不完整。", e);
            // 不抛出异常，以便 ByteToMessageDecoder 能够再次尝试
        } catch (Exception e) {
            // 其他运行时异常，直接抛出
            in.resetReaderIndex(); // 发生其他异常时也回滚，防止读指针错位
            log.error("MsgPackDecoder: 解码消息失败。", e);
            throw e;
        }
    }
}