package source.hanger.server.codec.msgpack.impl.deserializer;

import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import source.hanger.core.message.command.TimeoutCommand;
import source.hanger.core.message.command.TimeoutCommand.TimeoutCommandBuilder;
import source.hanger.server.codec.msgpack.base.BaseCommandPackDeserializer;

import java.io.IOException;

/**
 * TimeoutCommand 的 MessagePack 反序列化器。
 */
public class TimeoutCommandPackDeserializer extends
    BaseCommandPackDeserializer<TimeoutCommand, TimeoutCommandBuilder<?, ?>> {

    @Override
    protected TimeoutCommandBuilder<?, ?> builder() {
        return TimeoutCommand.builder();
    }

    @Override
    protected TimeoutCommandBuilder<?, ?> deserialize(MessageUnpacker unpacker, TimeoutCommandBuilder<?, ?> builder) throws IOException {
        // 首先调用基类的 deserialize 方法来反序列化 Message 和 Command 的核心字段
        // 注意：BaseCommandPackDeserializer 已经处理了 Message 的 id, srcLoc, destLocs, name, timestamp, properties
        // 以及 Command 的 parentCommandId
        // 这里只需要处理 TimeoutCommand 特有的字段

        // timerId (Long)
        Value timerIdValue = unpacker.unpackValue();
        if (!timerIdValue.isNilValue()) {
            builder.timerId(timerIdValue.asIntegerValue().asLong());
        }

        return builder;
    }
}
