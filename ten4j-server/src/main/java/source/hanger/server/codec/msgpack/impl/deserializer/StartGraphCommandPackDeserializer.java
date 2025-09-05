package source.hanger.server.codec.msgpack.impl.deserializer;

import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import source.hanger.core.message.command.StartGraphCommand;
import source.hanger.core.message.command.StartGraphCommand.StartGraphCommandBuilder;
import source.hanger.server.codec.msgpack.base.BaseCommandPackDeserializer;

import java.io.IOException;

/**
 * StartGraphCommand 的 MessagePack 反序列化器。
 */
public class StartGraphCommandPackDeserializer extends
    BaseCommandPackDeserializer<StartGraphCommand, StartGraphCommandBuilder<?, ?>> {

    @Override
    protected StartGraphCommandBuilder<?, ?> builder() {
        return StartGraphCommand.builder();
    }

    @Override
    protected StartGraphCommandBuilder<?, ?> deserialize(MessageUnpacker unpacker, StartGraphCommandBuilder<?, ?> builder)
        throws IOException {
        // 首先调用基类的 deserialize 方法来反序列化 Message 和 Command 的核心字段
        // 注意：BaseCommandPackDeserializer 已经处理了 Message 的 id, srcLoc, destLocs, name, timestamp, properties
        // 以及 Command 的 parentCommandId
        // 这里只需要处理 StartGraphCommand 特有的字段
        // predefinedGraphName (String)
        Value predefinedGraphNameValue = unpacker.unpackValue();
        if (!predefinedGraphNameValue.isNilValue()) {
            builder.predefinedGraphName(predefinedGraphNameValue.asStringValue().asString());
        }

        // graphJson (String)
        Value graphJsonValue = unpacker.unpackValue();
        if (!graphJsonValue.isNilValue()) {
            builder.graphJson(graphJsonValue.asStringValue().asString());
        }

        return builder;
    }
}
