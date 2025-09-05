package source.hanger.server.codec.msgpack.base;

import org.msgpack.core.MessagePacker;
import source.hanger.core.message.command.Command;

import java.io.IOException;
/**
 * 抽象基类，用于提供所有 Command 类型的 MessagePack 序列化器的通用结构。
 * 继承自 BaseMessagePackSerializer，并在此处处理 Command 特有字段的序列化逻辑（如果存在）。
 * @param <C> 序列化的源对象类型，必须是 Command 的子类
 */
public abstract class BaseCommandPackSerializer<C extends Command> extends BaseMessagePackSerializer<C> {

    @Override
    protected void serializeInternal(MessagePacker packer, C target) throws IOException {
        // Command 类目前没有特有字段需要在这里序列化。
        // 子类将在此基础上实现自己的特有字段序列化。
        // 如果 Command 以后有特有字段，将在这里实现。
    }

    @Override
    public void serialize(MessagePacker packer, C target) throws IOException {
        super.serialize(packer, target);
    }
}
