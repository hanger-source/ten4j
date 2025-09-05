package source.hanger.server.codec.msgpack.impl.serializer;

import org.msgpack.core.MessagePacker;
import source.hanger.core.message.CommandResult;
import source.hanger.server.codec.msgpack.base.BaseMessagePackSerializer;

import java.io.IOException;
import java.util.Objects;

/**
 * CommandResult 的 MessagePack 序列化器。
 */
public class CommandResultPackSerializer extends BaseMessagePackSerializer<CommandResult> {

    @Override
    protected void serializeInternal(MessagePacker packer, CommandResult target) throws IOException {
        // 序列化 CommandResult 的特有字段：originalCommandId, originalCmdType, originalCmdName, statusCode, isFinal, isCompleted
        packer.packString(Objects.requireNonNullElse(target.getOriginalCommandId(), ""));
        packer.packString(Objects.requireNonNull(target.getOriginalCmdType(), "originalCmdType cannot be null").name());
        packer.packString(Objects.requireNonNullElse(target.getOriginalCmdName(), ""));
        packer.packInt(Objects.requireNonNull(target.getStatusCode(), "statusCode cannot be null").getValue());
        packer.packBoolean(target.getIsFinal());
        packer.packBoolean(target.getIsCompleted());
    }

    @Override
    protected int getSpecificFieldCount() {
        // CommandResult 有 6 个特有字段：originalCommandId, originalCmdType, originalCmdName, statusCode, isFinal, isCompleted
        return 6;
    }
}
