package source.hanger.server.codec.msgpack.impl.deserializer;

import org.msgpack.core.MessageUnpacker;
import source.hanger.core.common.StatusCode;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.CommandResult.CommandResultBuilder;
import source.hanger.core.message.MessageType;
import source.hanger.server.codec.msgpack.base.BaseMessagePackDeserializer;

import java.io.IOException;

/**
 * CommandResult 的 MessagePack 反序列化器。
 */
public class CommandResultPackDeserializer extends
    BaseMessagePackDeserializer<CommandResult, CommandResultBuilder<?, ?>> {

    @Override
    protected CommandResultBuilder<?, ?> builder() {
        return CommandResult.builder();
    }

    @Override
    protected CommandResultBuilder<?, ?> deserialize(MessageUnpacker unpacker, CommandResultBuilder<?, ?> builder) throws IOException {
        // 反序列化 CommandResult 特有字段
        // originalCommandId
        builder.originalCommandId(unpacker.unpackString());

        // originalCmdType
        builder.originalCmdType(MessageType.fromString(unpacker.unpackString()));

        // originalCmdName
        builder.originalCmdName(unpacker.unpackString());

        // statusCode
        builder.statusCode(StatusCode.valueOf(unpacker.unpackString()));

        // isFinal
        builder.isFinal(unpacker.unpackBoolean());

        // isCompleted
        builder.isCompleted(unpacker.unpackBoolean());

        return builder;
    }
}
