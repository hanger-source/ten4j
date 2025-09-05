package source.hanger.server.codec.msgpack.impl.serializer;

import org.msgpack.core.MessagePacker;
import source.hanger.core.message.command.TimeoutCommand;
import source.hanger.server.codec.msgpack.base.BaseCommandPackSerializer;

import java.io.IOException;

/**
 * TimeoutCommand 的 MessagePack 序列化器。
 */
public class TimeoutCommandPackSerializer extends BaseCommandPackSerializer<TimeoutCommand> {

    @Override
    protected void serializeInternal(MessagePacker packer, TimeoutCommand target) throws IOException {
        // 序列化 TimeoutCommand 的特有字段：timerId
        packer.packLong(target.getTimerId());
    }

    @Override
    protected int getSpecificFieldCount() {
        // TimeoutCommand 有一个特有字段：timerId
        return 1;
    }
}
