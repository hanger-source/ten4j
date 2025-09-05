package source.hanger.server.codec.msgpack.impl.deserializer;

import io.netty.buffer.Unpooled; // 引入 Unpooled 用于创建 ByteBuf
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.VideoFrameMessage.VideoFrameMessageBuilder;
import source.hanger.server.codec.msgpack.base.BaseMessagePackDeserializer;

import java.io.IOException;

/**
 * VideoFrameMessage 的 MessagePack 反序列化器。
 */
public class VideoFrameMessagePackDeserializer extends
    BaseMessagePackDeserializer<VideoFrameMessage, VideoFrameMessageBuilder<?, ?>> {

    @Override
    protected VideoFrameMessageBuilder<?, ?> builder() {
        return VideoFrameMessage.builder();
    }

    @Override
    protected VideoFrameMessageBuilder<?, ?> deserialize(MessageUnpacker unpacker, VideoFrameMessageBuilder<?, ?> builder) throws IOException {
        // 首先调用基类的 deserialize 方法来反序列化 Message 的核心字段
        // 注意：BaseMessagePackDeserializer 已经处理了 Message 的 id, srcLoc, destLocs, name, timestamp, properties
        // 这里只需要处理 VideoFrameMessage 特有的字段

        // pixelFormat (Integer)
        builder.pixelFormat(unpacker.unpackInt());

        // frameTimestamp (long)
        builder.frameTimestamp(unpacker.unpackLong());

        // width (int)
        builder.width(unpacker.unpackInt());

        // height (int)
        builder.height(unpacker.unpackInt());

        // isEof (boolean)
        builder.isEof(unpacker.unpackBoolean());

        // data (ByteBuf)
        Value dataValue = unpacker.unpackValue();
        if (!dataValue.isNilValue()) {
            // 将 MessagePack 的 Binary Value 转换为 byte[]，然后用 Unpooled.wrappedBuffer 包装成 ByteBuf
            byte[] rawBytes = dataValue.asBinaryValue().asByteArray();
            builder.data(Unpooled.wrappedBuffer(rawBytes));
        } else {
            builder.data(null);
        }

        return builder;
    }
}
