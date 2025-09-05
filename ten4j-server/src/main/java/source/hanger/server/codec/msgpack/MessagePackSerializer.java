package source.hanger.server.codec.msgpack;

import org.msgpack.core.MessagePacker;

import java.io.IOException;

public interface MessagePackSerializer<T> {
    /**
     * 序列化给定的对象到 MessagePacker。
     * @param packer MessagePacker 实例
     * @param target 待序列化的对象
     * @throws IOException IO 异常
     */
    void serialize(MessagePacker packer, T target) throws IOException;
}
