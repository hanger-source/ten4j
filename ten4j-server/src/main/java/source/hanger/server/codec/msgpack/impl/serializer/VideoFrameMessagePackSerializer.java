package source.hanger.server.codec.msgpack.impl.serializer;

import io.netty.buffer.ByteBufUtil;
import org.msgpack.core.MessagePacker;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.util.ByteBufUtils;
import source.hanger.server.codec.msgpack.base.BaseMessagePackSerializer;

import java.io.IOException;
import java.util.Objects;

/**
 * VideoFrameMessage 的 MessagePack 序列化器。
 */
public class VideoFrameMessagePackSerializer extends BaseMessagePackSerializer<VideoFrameMessage> {

    @Override
    protected void serializeInternal(MessagePacker packer, VideoFrameMessage target) throws IOException {
        // 序列化 VideoFrameMessage 的特有字段：pixelFormat, frameTimestamp, width, height, isEof, data
        packer.packInt(Objects.requireNonNullElse(target.getPixelFormat(), 0)); // pixelFormat 假定可以为0
        packer.packLong(target.getFrameTimestamp());
        packer.packInt(target.getWidth());
        packer.packInt(target.getHeight());
        packer.packBoolean(target.isEof());

        // 序列化 data (ByteBuf)
        // 序列化 buf (ByteBuf)
        if (target.getData() == null) {
            packer.packNil();
        } else {
            int readableBytes = target.getData().readableBytes();
            packer.packBinaryHeader(readableBytes);
            ByteBufUtils.writeByteBufPayloadToPacker(packer, target.getData());
        }
    }

    @Override
    protected int getSpecificFieldCount() {
        // VideoFrameMessage 有 6 个特有字段：pixelFormat, frameTimestamp, width, height, isEof, data
        return 6;
    }
}
