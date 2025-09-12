package source.hanger.core.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled; // 导入 Unpooled
import org.msgpack.core.MessagePacker;

import java.io.IOException;
import java.nio.ByteBuffer; // 导入 ByteBuffer

public class ByteBufUtils {

    /**
     * 将 ByteBuf 的内容 (payload) 写入 MessagePacker。
     * 该方法假定：
     * 1. 传入的 ByteBuf 非空。
     * 2. 调用者已经根据 ByteBuf 的可读字节数，写入了 MessagePack 的 binary header。
     *
     * 该方法会尝试零拷贝写入：
     * - 如果 ByteBuf 是堆缓冲区 (has a backing array)，直接使用其底层数组写入。
     * - 对于非堆缓冲区 (例如直接缓冲区)，将 ByteBuf 的可读内容复制到临时 byte[] 中再写入。
     * 确保 ByteBuf 的读索引被正确推进，以反映已写入的字节。
     *
     * @param packer  MessagePacker 实例
     * @param byteBuf 待序列化的 ByteBuf (假设非空且可读字节数已与 binary header 匹配)
     * @throws IOException 如果写入失败
     */
    public static void writeByteBufPayloadToPacker(MessagePacker packer, ByteBuf byteBuf) throws IOException {
        // 假设 byteBuf 非空，且 binary header 已由调用者写入。
        // 这里只处理实际的 payload 写入。

        int readableBytes = byteBuf.readableBytes();

        if (readableBytes == 0) {
            // 如果没有可读字节，直接返回，无需操作
            return;
        }

        // 优先使用零拷贝：如果 ByteBuf 是一个堆缓冲区 (has a backing array)，直接使用其底层数组
        if (byteBuf.hasArray()) {
            packer.writePayload(byteBuf.array(), byteBuf.arrayOffset() + byteBuf.readerIndex(), readableBytes);
            // 手动推进 ByteBuf 的读索引，表示这些字节已被“读取”
            byteBuf.readerIndex(byteBuf.readerIndex() + readableBytes);
        } else {
            // 对于非堆缓冲区 (例如直接缓冲区)，需要复制数据。
            // 使用 ByteBuf.readBytes(byte[]) 方法，它会复制数据并自动推进读索引。
            byte[] bytes = new byte[readableBytes];
            byteBuf.readBytes(bytes); // 这会将数据从 ByteBuf 复制到 bytes 数组，并推进读索引
            packer.writePayload(bytes);
        }
    }

    /**
     * 将 Java NIO ByteBuffer 转换为 Netty ByteBuf。
     * 返回的 ByteBuf 是对原始 ByteBuffer 的一个零拷贝包装，
     * 其内部数据与 ByteBuffer 共享。对 ByteBuf 的写入会反映在 ByteBuffer 中，反之亦然。
     * ByteBuf 的 readerIndex 和 writerIndex 会根据 ByteBuffer 的 position 和 limit 设置。
     * 如果传入的 ByteBuffer 为 null，将返回 null。
     *
     * @param byteBuffer 待转换的 Java NIO ByteBuffer
     * @return 转换后的 Netty ByteBuf，如果 ByteBuffer 为 null 则返回 null。
     */
    public static ByteBuf fromByteBuffer(ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return Unpooled.EMPTY_BUFFER; // 返回一个空的 ByteBuf 而不是 null
        }
        // 使用 Unpooled.wrappedBuffer(ByteBuffer) 进行零拷贝包装
        return Unpooled.wrappedBuffer(byteBuffer);
    }

    /**
     * 将 Netty ByteBuf 的可读部分转换为 Java NIO ByteBuffer。
     * 该方法返回的 ByteBuffer 是原始 ByteBuf 可读区域的一个视图，
     * 它的 position 和 limit 会根据 ByteBuf 的 readerIndex 和 readableBytes 设置。
     * 对返回的 ByteBuffer 的 position 和 limit 的修改不会影响原始 ByteBuf 的 readerIndex 和 writerIndex。
     * 但如果 ByteBuf 是堆缓冲区，且 ByteBuffer 被修改，则可能影响原始 ByteBuf 的内容。
     * 如果传入的 ByteBuf 为 null 或没有可读字节，将返回 ByteBuffer.allocate(0)。
     *
     * @param byteBuf 待转换的 Netty ByteBuf
     * @return 转换后的 Java NIO ByteBuffer，如果 ByteBuf 为 null 或无数据则返回 ByteBuffer.allocate(0)。
     */
    public static ByteBuffer toByteBuffer(ByteBuf byteBuf) {
        if (byteBuf == null || !byteBuf.isReadable()) {
            return ByteBuffer.allocate(0);
        }
        // 使用 nioBuffer() 方法获取一个 Java NIO ByteBuffer。
        // 这个 ByteBuffer 会共享 ByteBuf 的内部存储（无论是堆缓冲区还是直接缓冲区），
        // 并且其 position 和 limit 会被设置为 ByteBuf 的 readerIndex 和 writerIndex。
        // 返回的 ByteBuffer 是 ByteBuf 可读区域的一个视图，对其 position/limit 的修改不会影响 ByteBuf。
        return byteBuf.nioBuffer();
    }

    /**
     * 将 byte[] 数组的指定部分转换为 Java NIO ByteBuffer。
     * 该方法会创建一个新的 ByteBuffer，并复制指定范围的字节数据到其中。
     * 如果传入的 byte[] 为 null 或指定范围无效，将返回 ByteBuffer.allocate(0)。
     *
     * @param bytes  原始字节数组
     * @param offset 偏移量
     * @param length 长度
     * @return 包含指定字节数据的 Java NIO ByteBuffer
     */
    public static ByteBuffer toByteBuffer(byte[] bytes, int offset, int length) {
        if (bytes == null || offset < 0 || length < 0 || offset + length > bytes.length) {
            return ByteBuffer.allocate(0);
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        buffer.put(bytes, offset, length);
        buffer.flip(); // 将 limit 设置为当前 position，然后将 position 设置为 0
        return buffer;
    }

    /**
     * 将 byte[] 数组的指定部分转换为 Netty ByteBuf。
     * 该方法会创建一个新的 ByteBuf，并复制指定范围的字节数据到其中。
     * 如果传入的 byte[] 为 null 或指定范围无效，将返回 Unpooled.EMPTY_BUFFER。
     *
     * @param bytes  原始字节数组
     * @param offset 偏移量
     * @param length 长度
     * @return 包含指定字节数据的 Netty ByteBuf
     */
    public static ByteBuf toByteBuf(byte[] bytes, int offset, int length) {
        if (bytes == null || offset < 0 || length < 0 || offset + length > bytes.length) {
            return Unpooled.EMPTY_BUFFER;
        }
        ByteBuffer byteBuffer = toByteBuffer(bytes, offset, length);
        return fromByteBuffer(byteBuffer);
    }
}
