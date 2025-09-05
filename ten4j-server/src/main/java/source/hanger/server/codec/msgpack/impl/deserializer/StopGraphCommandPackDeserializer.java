package source.hanger.server.codec.msgpack.impl.deserializer;

import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import source.hanger.core.message.command.StopGraphCommand;
import source.hanger.core.message.command.StopGraphCommand.StopGraphCommandBuilder;
import source.hanger.server.codec.msgpack.base.BaseCommandPackDeserializer;

import java.io.IOException;

/**
 * StopGraphCommand 的 MessagePack 反序列化器。
 */
public class StopGraphCommandPackDeserializer extends
    BaseCommandPackDeserializer<StopGraphCommand, StopGraphCommandBuilder<?, ?>> {

    @Override
    protected StopGraphCommandBuilder<?, ?> builder() {
        return StopGraphCommand.builder();
    }

    @Override
    protected StopGraphCommandBuilder<?, ?> deserialize(MessageUnpacker unpacker, StopGraphCommandBuilder<?, ?> builder) throws IOException {
        // 首先调用基类的 deserialize 方法来反序列化 Message 和 Command 的核心字段
        // 注意：BaseCommandPackDeserializer 已经处理了 Message 的 id, srcLoc, destLocs, name, timestamp, properties
        // 以及 Command 的 parentCommandId
        // 这里只需要处理 StopGraphCommand 特有的字段

        // graphId (String)
        Value graphIdValue = unpacker.unpackValue();
        if (!graphIdValue.isNilValue()) {
            builder.graphId(graphIdValue.asStringValue().asString());
        }

        return builder;
    }
}
