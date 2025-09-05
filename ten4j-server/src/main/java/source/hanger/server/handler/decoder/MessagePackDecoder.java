package source.hanger.server.handler.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.msgpack.core.ExtensionTypeHeader;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import source.hanger.core.message.Message;
import source.hanger.server.codec.msgpack.MessageExtensionConstants;
import source.hanger.server.codec.msgpack.MessagePackDeserializerFacade;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Netty 解码器，用于将接收到的 ByteBuf 解码为 Message 对象。
 * 它负责处理 MessagePack EXT 类型的解封装，并将 EXT 负载传递给 MessagePackDeserializerFacade 进行实际的反序列化。
 * 不包含任何日志。
 */
public class MessagePackDecoder extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(MessagePackDecoder.class);
    private final MessagePackDeserializerFacade deserializerFacade;

    public MessagePackDecoder() {
        this.deserializerFacade = new MessagePackDeserializerFacade();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 确保至少有 EXT 头部所需的最小字节数（1字节格式码 + 1字节类型码 + 1字节长度）
        if (in.readableBytes() < 3) {
            return;
        }

        // 标记当前的读取位置，以便回滚
        in.markReaderIndex();

        // 尝试从 ByteBuf 中读取 EXT 头部。
        // MessageUnpacker 需要 ByteBuffer。这里我们使用 ByteBuf.nioBuffer() 创建一个共享的 NIO ByteBuffer 视图。
        // 注意：nioBuffer() 不会改变 ByteBuf 的 readerIndex，我们需要手动前进。
        ByteBuffer nioBuffer = in.nioBuffer(in.readerIndex(), in.readableBytes());
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(nioBuffer);

        try {
            ExtensionTypeHeader extHeader = unpacker.unpackExtensionTypeHeader();
            byte extType = extHeader.getType();
            int extLength = extHeader.getLength();

            // 如果当前可读字节不足以包含整个 EXT 负载，回滚并等待更多数据
            if (in.readableBytes() < (unpacker.getTotalReadBytes() + extLength)) {
                in.resetReaderIndex();
                return;
            }

            // 验证 EXT 类型
            if (extType != MessageExtensionConstants.TEN_MSGPACK_EXT_TYPE_MSG) {
                // 如果 EXT 类型不匹配，跳过此消息或抛出异常，这里选择跳过并推进 readerIndex
                // 不抛出异常，不打印日志，只是简单跳过不认识的 EXT 类型
                // 推进 readerIndex 跳过整个 EXT 帧
                in.readerIndex((int)(in.readerIndex() + unpacker.getTotalReadBytes() + extLength));
                return;
            }

            // 推进 ByteBuf 的 readerIndex 到 EXT 负载的开始
            in.readerIndex((int)(in.readerIndex() + unpacker.getTotalReadBytes()));

            // 检查 EXT 负载是否完全可读
            if (in.readableBytes() < extLength) {
                in.resetReaderIndex();
                return;
            }

            // 读取 EXT 负载数据
            byte[] payload = new byte[extLength];
            in.readBytes(payload);

            // 使用负载数据创建新的 MessageUnpacker，并传递给 deserializerFacade
            MessageUnpacker payloadUnpacker = MessagePack.newDefaultUnpacker(payload);
            try {
                Message message = deserializerFacade.deserialize(payloadUnpacker);
                if (message != null) {
                    out.add(message);
                }
            } catch (Throwable throwable) {
                log.error("DECODER_TRACE: MessagePackDecoder: 解析 EXT 帧失败。", throwable);
            }finally {
                payloadUnpacker.close();
            }

        } catch (IOException e) {
            log.error("DECODER_TRACE: MessagePackDecoder: 解析 EXT 帧失败。", e);
            // 发生 IO 异常，可能表示数据损坏或格式不正确。
            // 这里不打印日志，直接抛出，让 Netty 默认的异常处理机制介入。
            throw e;
        } finally {
            unpacker.close(); // 确保关闭 unpacker
        }
    }
}
