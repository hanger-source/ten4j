package source.hanger.server.codec.msgpack.impl.serializer;

import io.netty.buffer.ByteBuf;
import org.msgpack.core.MessagePacker;
import source.hanger.core.message.DataMessage;
import source.hanger.server.codec.msgpack.base.BaseMessagePackSerializer;
import source.hanger.core.util.ByteBufUtils; // 导入新的工具类

import java.io.IOException;

/**
 * DataMessage 的 MessagePack 序列化器。
 */
public class DataMessagePackSerializer extends BaseMessagePackSerializer<DataMessage> {

    @Override
    protected void serializeInternal(MessagePacker packer, DataMessage target) throws IOException {
        ByteBuf dataBuf = target.getData();

        if (dataBuf == null) {
            // 如果数据为空，序列化为 MessagePack 的 nil 类型
            packer.packNil();
        } else {
            // 写入 Binary Header
            int readableBytes = dataBuf.readableBytes();
            packer.packBinaryHeader(readableBytes);

            // 使用工具类方法写入 ByteBuf 的 payload
            ByteBufUtils.writeByteBufPayloadToPacker(packer, dataBuf);
        }
    }

    @Override
    protected int getSpecificFieldCount() {
        // DataMessage 有一个特有字段：data
        return 1;
    }
}
