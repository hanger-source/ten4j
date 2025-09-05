package source.hanger.server.codec.msgpack.impl.deserializer;

import io.netty.buffer.Unpooled; // 引入 Unpooled 用于创建 ByteBuf
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.AudioFrameMessage.AudioFrameMessageBuilder;
import source.hanger.server.codec.msgpack.base.BaseMessagePackDeserializer;

import java.io.IOException;

/**
 * AudioFrameMessage 的 MessagePack 反序列化器。
 */
public class AudioFrameMessagePackDeserializer extends
    BaseMessagePackDeserializer<AudioFrameMessage, AudioFrameMessageBuilder<?, ?>> {

    @Override
    protected AudioFrameMessageBuilder<?, ?> builder() {
        return AudioFrameMessage.builder();
    }

    @Override
    protected AudioFrameMessageBuilder<?, ?> deserialize(MessageUnpacker unpacker, AudioFrameMessageBuilder<?, ?> builder) throws IOException {
        // 首先调用基类的 deserialize 方法来反序列化 Message 的核心字段
        // 注意：BaseMessagePackDeserializer 已经处理了 Message 的 id, srcLoc, destLocs, name, timestamp, properties
        // 这里只需要处理 AudioFrameMessage 特有的字段

        // frameTimestamp (Long)
        builder.frameTimestamp(unpacker.unpackLong());

        // sampleRate (Integer)
        builder.sampleRate(unpacker.unpackInt());

        // bytesPerSample (Integer)
        builder.bytesPerSample(unpacker.unpackInt());

        // samplesPerChannel (Integer)
        builder.samplesPerChannel(unpacker.unpackInt());

        // numberOfChannel (Integer)
        builder.numberOfChannel(unpacker.unpackInt());

        // channelLayout (Long)
        builder.channelLayout(unpacker.unpackLong());

        // dataFormat (Integer)
        builder.dataFormat(unpacker.unpackInt());

        // buf (ByteBuf)
        Value bufValue = unpacker.unpackValue();
        if (!bufValue.isNilValue()) {
            // 将 MessagePack 的 Binary Value 转换为 byte[]，然后用 Unpooled.wrappedBuffer 包装成 ByteBuf
            byte[] rawBytes = bufValue.asBinaryValue().asByteArray();
            builder.buf(Unpooled.wrappedBuffer(rawBytes));
        } else {
            builder.buf(null);
        }

        // lineSize (Integer)
        builder.lineSize(unpacker.unpackInt());

        // isEof (Boolean)
        Value isEofValue = unpacker.unpackValue();
        if (!isEofValue.isNilValue()) {
            builder.isEof(isEofValue.asBooleanValue().getBoolean());
        } else {
            builder.isEof(false); // 默认为 false
        }

        return builder;
    }
}
