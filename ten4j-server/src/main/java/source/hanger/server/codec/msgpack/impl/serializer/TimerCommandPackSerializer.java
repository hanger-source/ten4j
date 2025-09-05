package source.hanger.server.codec.msgpack.impl.serializer;

import org.msgpack.core.MessagePacker;
import source.hanger.core.message.command.TimerCommand;
import source.hanger.server.codec.msgpack.base.BaseCommandPackSerializer;

import java.io.IOException;

/**
 * TimerCommand 的 MessagePack 序列化器。
 */
public class TimerCommandPackSerializer extends BaseCommandPackSerializer<TimerCommand> {

    @Override
    protected void serializeInternal(MessagePacker packer, TimerCommand target) throws IOException {
        // 序列化 TimerCommand 的特有字段：timerId, timeoutUs, times
        packer.packLong(target.getTimerId());
        packer.packLong(target.getTimeoutUs());
        packer.packInt(target.getTimes());
    }

    @Override
    protected int getSpecificFieldCount() {
        // TimerCommand 有三个特有字段：timerId, timeoutUs, times
        return 3;
    }
}
