package source.hanger.server.handler.encoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import source.hanger.core.message.Message;
import source.hanger.server.codec.msgpack.MessageExtensionConstants;
import source.hanger.server.codec.msgpack.MessagePackSerializerFacade;

import java.io.IOException;
import java.io.OutputStream;

@Slf4j
public class MessagePackEncoder extends MessageToByteEncoder<Message> {

    private final MessagePackSerializerFacade serializerFacade;

    public MessagePackEncoder() {
        this.serializerFacade = new MessagePackSerializerFacade();
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        // 1. 记录 EXT 头部起始位置
        int extHeaderStartIndex = out.writerIndex();

        // 2. 预留 EXT 头部空间 (最多 6 字节: 1字节格式码 + 4字节长度 + 1字节类型码)
        // MessagePacker.packExtensionTypeHeader() 会根据实际长度自动选择 EXT8/EXT16/EXT32，
        // 但我们这里需要先预留出足够大的空间。
        out.writeZero(6); // 预留 6 字节，足够容纳 EXT32 头部，用零填充

        // 3. 记录 Payload 起始位置
        int payloadStartIndex = out.writerIndex();

        try (MessagePacker payloadPacker = MessagePack.newDefaultPacker(new ByteBufOutputStream(out))) {
            // 4. 序列化消息 Payload 到 ByteBuf
            serializerFacade.serialize(payloadPacker, msg);
            payloadPacker.flush(); // 确保所有数据都写入 ByteBuf
        }

        // 5. 计算 Payload 长度
        int payloadLength = out.writerIndex() - payloadStartIndex;

        // 6. 回填 EXT 头部
        int currentWriterIndex = out.writerIndex(); // 保存当前写入位置
        out.writerIndex(extHeaderStartIndex); // 将写入位置移回 EXT 头部起始

        // 使用 MessagePacker 写入 EXT 头部。
        // MessagePacker 会根据 payloadLength 自动选择最合适的 EXT 格式 (EXT8, EXT16, EXT32)。
        try (MessagePacker extHeaderPacker = MessagePack.newDefaultPacker(new ByteBufOutputStream(out))) {
            extHeaderPacker.packExtensionTypeHeader(MessageExtensionConstants.TEN_MSGPACK_EXT_TYPE_MSG, payloadLength);
            extHeaderPacker.flush();
        }

        // 实际写入的 EXT 头部长度
        int actualExtHeaderLength = out.writerIndex() - extHeaderStartIndex;

        // 如果预留空间大于实际头部长度，则需要移动数据
        if (6 > actualExtHeaderLength) {
            int shiftAmount = 6 - actualExtHeaderLength;
            // 移动 payload 数据向前
            out.setBytes(extHeaderStartIndex + actualExtHeaderLength, out, payloadStartIndex, payloadLength);
            // 更新 writerIndex 到正确的位置
            out.writerIndex(currentWriterIndex - shiftAmount);
        } else {
            // 如果实际头部长度等于或大于预留空间，不需要移动数据，直接恢复 writerIndex
            out.writerIndex(currentWriterIndex);
        }
    }

    /**
     * 内部类，将 OutputStream 适配到 Netty 的 ByteBuf。
     * 允许 MessagePacker 直接写入 ByteBuf。
     */
    private static class ByteBufOutputStream extends OutputStream {
        private final ByteBuf buffer;

        public ByteBufOutputStream(ByteBuf buffer) {
            this.buffer = buffer;
        }

        @Override
        public void write(int b) throws IOException {
            buffer.writeByte(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            buffer.writeBytes(b, off, len);
        }

        @Override
        public void write(byte[] b) throws IOException {
            buffer.writeBytes(b);
        }
    }
}