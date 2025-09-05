package source.hanger.server.codec.msgpack.impl.serializer;

import org.msgpack.core.MessagePacker;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.server.codec.msgpack.base.BaseCommandPackSerializer;

import java.io.IOException;

/**
 * GenericCommand 的 MessagePack 序列化器。
 * 由于 GenericCommand 没有特有字段，其序列化逻辑将主要依赖 BaseCommandPackSerializer。
 */
public class GenericCommandPackSerializer extends BaseCommandPackSerializer<GenericCommand> {

    @Override
    protected void serializeInternal(MessagePacker packer, GenericCommand target) throws IOException {
        // GenericCommand 没有特有字段需要在这里序列化。
        // 基类 BaseCommandPackSerializer 已经处理了 Message 和 Command 的核心字段。
    }

    @Override
    protected int getSpecificFieldCount() {
        // GenericCommand 没有特有字段，所以返回 0。
        return 0;
    }
}
