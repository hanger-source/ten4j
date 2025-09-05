package source.hanger.server.codec.msgpack.impl.deserializer;

import org.msgpack.core.MessageUnpacker;
import source.hanger.core.message.command.CloseAppCommand;
import source.hanger.core.message.command.CloseAppCommand.CloseAppCommandBuilder;
import source.hanger.server.codec.msgpack.base.BaseCommandPackDeserializer;

import java.io.IOException;

/**
 * CloseAppCommand 的 MessagePack 反序列化器。
 * 由于 CloseAppCommand 没有特有字段，其反序列化逻辑将主要依赖 BaseCommandPackDeserializer。
 */
public class CloseAppCommandPackDeserializer extends
    BaseCommandPackDeserializer<CloseAppCommand, CloseAppCommandBuilder<?, ?>> {

    @Override
    protected CloseAppCommandBuilder<?, ?> builder() {
        return CloseAppCommand.builder();
    }

    @Override
    public CloseAppCommandBuilder<?, ?> deserialize(MessageUnpacker unpacker) throws IOException {
        // 首先调用基类的 deserialize 方法来反序列化 Message 和 Command 的核心字段
        CloseAppCommandBuilder<?, ?> builder = super.deserialize(unpacker);

        // CloseAppCommand 没有特有字段，所以这里不需要额外的反序列化逻辑

        return builder;
    }
}
