package source.hanger.server.codec.msgpack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.msgpack.core.MessageUnpacker;
import source.hanger.core.message.Message;
import source.hanger.core.message.Message.MessageBuilder;
import source.hanger.core.message.MessageType;
import source.hanger.server.codec.msgpack.impl.deserializer.AudioFrameMessagePackDeserializer;
import source.hanger.server.codec.msgpack.impl.deserializer.CloseAppCommandPackDeserializer;
import source.hanger.server.codec.msgpack.impl.deserializer.CommandResultPackDeserializer;
import source.hanger.server.codec.msgpack.impl.deserializer.DataMessagePackDeserializer;
import source.hanger.server.codec.msgpack.impl.deserializer.GenericCommandPackDeserializer;
import source.hanger.server.codec.msgpack.impl.deserializer.StartGraphCommandPackDeserializer;
import source.hanger.server.codec.msgpack.impl.deserializer.StopGraphCommandPackDeserializer;
import source.hanger.server.codec.msgpack.impl.deserializer.TimeoutCommandPackDeserializer;
import source.hanger.server.codec.msgpack.impl.deserializer.TimerCommandPackDeserializer;
import source.hanger.server.codec.msgpack.impl.deserializer.VideoFrameMessagePackDeserializer;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public class MessagePackDeserializerFacade implements MessagePackDeserializer<Message>{

    private static final Map<MessageType, MessagePackDeserializer<? extends MessageBuilder<?, ?>>> deserializers = new HashMap<>();

    static {
        // 注册具体的反序列化器
        deserializers.put(MessageType.CMD, new GenericCommandPackDeserializer());
        deserializers.put(MessageType.CMD_CLOSE_APP, new CloseAppCommandPackDeserializer());
        deserializers.put(MessageType.CMD_RESULT, new CommandResultPackDeserializer());
        deserializers.put(MessageType.CMD_START_GRAPH, new StartGraphCommandPackDeserializer());
        deserializers.put(MessageType.CMD_STOP_GRAPH, new StopGraphCommandPackDeserializer());
        deserializers.put(MessageType.CMD_TIMER, new TimerCommandPackDeserializer());
        deserializers.put(MessageType.CMD_TIMEOUT, new TimeoutCommandPackDeserializer());
        deserializers.put(MessageType.DATA, new DataMessagePackDeserializer());
        deserializers.put(MessageType.VIDEO_FRAME, new VideoFrameMessagePackDeserializer());
        deserializers.put(MessageType.AUDIO_FRAME, new AudioFrameMessagePackDeserializer());
    }

    @Override
    public Message deserialize(MessageUnpacker unpacker) throws IOException {
        unpacker.unpackArrayHeader();
        String type = unpacker.unpackString();
        MessageType messageType = MessageType.fromString(type);
        MessagePackDeserializer<? extends MessageBuilder<?, ?>> deserializer = deserializers.get(messageType);
        if (deserializer != null) {
            return deserializer.deserialize(unpacker).build();
        }
        return null;
    }
}
