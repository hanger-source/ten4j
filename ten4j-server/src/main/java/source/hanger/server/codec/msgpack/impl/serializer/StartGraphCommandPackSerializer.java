package source.hanger.server.codec.msgpack.impl.serializer;

import org.msgpack.core.MessagePacker;
import source.hanger.core.message.command.StartGraphCommand;
import source.hanger.server.codec.msgpack.base.BaseCommandPackSerializer;

import java.io.IOException;
import java.util.Objects;

/**
 * StartGraphCommand 的 MessagePack 序列化器。
 */
public class StartGraphCommandPackSerializer extends BaseCommandPackSerializer<StartGraphCommand> {

    @Override
    protected void serializeInternal(MessagePacker packer, StartGraphCommand target) throws IOException {
        // 序列化 StartGraphCommand 的特有字段：predefinedGraphName 和 graphJson
        packer.packString(Objects.requireNonNullElse(target.getPredefinedGraphName(), ""));
        packer.packString(Objects.requireNonNullElse(target.getGraphJson(), ""));
    }

    @Override
    protected int getSpecificFieldCount() {
        // StartGraphCommand 有两个特有字段：predefinedGraphName 和 graphJson
        return 2;
    }
}
