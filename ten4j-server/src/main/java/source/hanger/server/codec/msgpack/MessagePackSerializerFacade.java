package source.hanger.server.codec.msgpack;

import org.msgpack.core.MessagePacker;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import source.hanger.server.codec.msgpack.impl.serializer.AudioFrameMessagePackSerializer;
import source.hanger.server.codec.msgpack.impl.serializer.CloseAppCommandPackSerializer;
import source.hanger.server.codec.msgpack.impl.serializer.CommandResultPackSerializer;
import source.hanger.server.codec.msgpack.impl.serializer.DataMessagePackSerializer;
import source.hanger.server.codec.msgpack.impl.serializer.StartGraphCommandPackSerializer;
import source.hanger.server.codec.msgpack.impl.serializer.StopGraphCommandPackSerializer;
import source.hanger.server.codec.msgpack.impl.serializer.TimeoutCommandPackSerializer;
import source.hanger.server.codec.msgpack.impl.serializer.TimerCommandPackSerializer;
import source.hanger.server.codec.msgpack.impl.serializer.VideoFrameMessagePackSerializer;
import source.hanger.server.codec.msgpack.impl.serializer.GenericCommandPackSerializer;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MessagePack 序列化器的门面类。
 * 负责将 Message 对象（及其子类）序列化为 MessagePack 数组，
 * 并根据 MessageType 委托给具体的序列化器。
 * 不再负责 EXT 类型封装。
 *
 * @author fuhangbo.hanger.uhfun
 */
public class MessagePackSerializerFacade implements MessagePackSerializer<Message> {

    // 存储 MessageType 到具体序列化器的映射
    private static final Map<MessageType, MessagePackSerializer<? extends Message>> serializers = new HashMap<>();

    static {
        // 注册所有具体的序列化器
        serializers.put(MessageType.CMD, new GenericCommandPackSerializer());
        serializers.put(MessageType.CMD_CLOSE_APP, new CloseAppCommandPackSerializer());
        serializers.put(MessageType.CMD_RESULT, new CommandResultPackSerializer());
        serializers.put(MessageType.CMD_START_GRAPH, new StartGraphCommandPackSerializer());
        serializers.put(MessageType.CMD_STOP_GRAPH, new StopGraphCommandPackSerializer());
        serializers.put(MessageType.CMD_TIMER, new TimerCommandPackSerializer());
        serializers.put(MessageType.CMD_TIMEOUT, new TimeoutCommandPackSerializer());
        serializers.put(MessageType.DATA, new DataMessagePackSerializer());
        serializers.put(MessageType.VIDEO_FRAME, new VideoFrameMessagePackSerializer());
        serializers.put(MessageType.AUDIO_FRAME, new AudioFrameMessagePackSerializer());
    }

    @Override
    public void serialize(MessagePacker packer, Message target) throws IOException {
        Objects.requireNonNull(target, "Message cannot be null for serialization.");

        MessageType messageType = target.getType();
        MessagePackSerializer<Message> specificSerializer =
            (MessagePackSerializer<Message>) serializers.get(messageType);

        if (specificSerializer == null) {
            throw new IOException(
                MessageFormat.format("No MessagePack serializer registered for MessageType: {0}", messageType));
        }

        // 直接委托给具体的序列化器来序列化整个 Message。
        // specificSerializer 将负责打包 MessagePack 数组头部和 type 字段，
        // 以及 Message 的核心字段和子类特有字段。
        specificSerializer.serialize(packer, target);
    }
}
