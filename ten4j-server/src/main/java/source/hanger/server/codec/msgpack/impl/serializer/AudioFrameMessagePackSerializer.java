package source.hanger.server.codec.msgpack.impl.serializer;

import org.msgpack.core.MessagePacker;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.server.codec.msgpack.base.BaseMessagePackSerializer;
import source.hanger.core.util.ByteBufUtils; // 导入新的工具类

import java.io.IOException;
// import java.util.Objects; // 不再需要 Objects.requireNonNull

/**
 * AudioFrameMessage 的 MessagePack 序列化器。
 */
public class AudioFrameMessagePackSerializer extends BaseMessagePackSerializer<AudioFrameMessage> {

    @Override
    protected void serializeInternal(MessagePacker packer, AudioFrameMessage target) throws IOException {
        // 序列化 AudioFrameMessage 的特有字段：
        if (target.getFrameTimestamp() == null) {
            packer.packNil();
        } else {
            packer.packLong(target.getFrameTimestamp());
        }
        packer.packInt(target.getSampleRate());
        packer.packInt(target.getBytesPerSample());
        packer.packInt(target.getSamplesPerChannel());
        packer.packInt(target.getNumberOfChannel());
        if (target.getChannelLayout() == null) {
            packer.packNil();
        } else {
            packer.packLong(target.getChannelLayout());
        }

        if (target.getDataFormat() == null) {
            packer.packNil();
        } else {
            packer.packInt(target.getDataFormat());
        }
        // 序列化 buf (ByteBuf)
        if (target.getBuf() == null) {
            packer.packNil();
        } else {
            int readableBytes = target.getBuf().readableBytes();
            packer.packBinaryHeader(readableBytes);
            ByteBufUtils.writeByteBufPayloadToPacker(packer, target.getBuf());
        }

        if (target.getLineSize() == null) {
            packer.packNil();
        } else {
            packer.packInt(target.getLineSize());
        }

        if (target.getIsEof() == null) {
            packer.packNil();
        } else {
            packer.packBoolean(target.getIsEof());
        }
    }

    @Override
    protected int getSpecificFieldCount() {
        // AudioFrameMessage 有 10 个特有字段：frameTimestamp, sampleRate, bytesPerSample, samplesPerChannel,
        // numberOfChannel, channelLayout, dataFormat, buf, lineSize, isEof
        return 10;
    }
}
