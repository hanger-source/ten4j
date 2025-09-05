package source.hanger.server.codec.msgpack.impl.serializer;

import org.msgpack.core.MessagePacker;
import source.hanger.core.message.command.StopGraphCommand;
import source.hanger.server.codec.msgpack.base.BaseCommandPackSerializer;

import java.io.IOException;
import java.util.Objects;

/**
 * StopGraphCommand 的 MessagePack 序列化器。
 */
public class StopGraphCommandPackSerializer extends BaseCommandPackSerializer<StopGraphCommand> {

    @Override
    protected void serializeInternal(MessagePacker packer, StopGraphCommand target) throws IOException {
        // 序列化 StopGraphCommand 的特有字段：graphId
        packer.packString(Objects.requireNonNullElse(target.getGraphId(), ""));
    }

    @Override
    protected int getSpecificFieldCount() {
        // StopGraphCommand 有一个特有字段：graphId
        return 1;
    }
}
