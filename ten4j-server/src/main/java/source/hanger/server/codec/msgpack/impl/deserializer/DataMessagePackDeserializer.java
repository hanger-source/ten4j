package source.hanger.server.codec.msgpack.impl.deserializer;

import io.netty.buffer.Unpooled; // 引入 Unpooled 用于创建 ByteBuf
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.DataMessage.DataMessageBuilder;
import source.hanger.server.codec.msgpack.base.BaseMessagePackDeserializer;

import java.io.IOException;

/**
 * DataMessage 的 MessagePack 反序列化器。
 */
public class DataMessagePackDeserializer extends
    BaseMessagePackDeserializer<DataMessage, DataMessageBuilder<?, ?>> {

    @Override
    protected DataMessageBuilder<?, ?> builder() {
        return DataMessage.builder();
    }

    @Override
    protected DataMessageBuilder<?, ?> deserialize(MessageUnpacker unpacker, DataMessageBuilder<?, ?> builder) throws IOException {
        // 首先调用基类的 deserialize 方法来反序列化 Message 的核心字段
        // 注意：BaseMessagePackDeserializer 已经处理了 Message 的 id, srcLoc, destLocs, name, timestamp, properties
        // 这里只需要处理 DataMessage 特有的字段

        // data (ByteBuf)
        Value dataValue = unpacker.unpackValue();
        if (!dataValue.isNilValue()) {
            // 将 MessagePack 的 Binary Value 转换为 byte[]，然后用 Unpooled.wrappedBuffer 包装成 ByteBuf
            byte[] rawBytes = dataValue.asBinaryValue().asByteArray();
            builder.data(Unpooled.wrappedBuffer(rawBytes));
        }

        return builder;
    }
}
