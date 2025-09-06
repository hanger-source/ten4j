package source.hanger.server.codec.msgpack.base;

import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import source.hanger.core.message.Message;
import source.hanger.core.message.Location;
import source.hanger.server.codec.msgpack.MessagePackDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 抽象基类，用于提供所有 MessagePack 反序列化器的通用结构。
 * 具体的消息类型反序列化器将继承此基类。
 * @param <T> 反序列化的目标对象类型，必须是 Message 的子类
 */
public abstract class BaseMessagePackDeserializer<T extends Message,  B extends Message.MessageBuilder<? extends T, ?>> implements
    MessagePackDeserializer<B> {

    protected abstract B builder();
    /**
     * 抽象的反序列化方法，由具体的子类实现。
     * 这个方法是 IMessagePackDeserializer 接口的实现，负责子类的实例化和特有字段的反序列化。
     * @param unpacker MessageUnpacker 实例
     * @return 反序列化后的对象
     * @throws IOException IO 异常
     */
    @Override
    public B deserialize(MessageUnpacker unpacker) throws IOException {
        // 解包 Array 头部
        B builder = builder();
        // 按照严格的、预定义的顺序解包字段
        builder.id(unpacker.unpackString());
        builder.srcLoc(deserializeLocation(unpacker));
        builder.destLocs(deserializeLocationList(unpacker));
        builder.name(unpacker.unpackString());
        builder.timestamp(unpacker.unpackLong());
        builder.properties(deserializeProperties(unpacker));
        return deserialize(unpacker, builder);
    }

    protected B deserialize(MessageUnpacker unpacker, B builder) throws IOException {
        return builder;
    }

    private Map<String, Object> deserializeProperties(MessageUnpacker unpacker) throws IOException {
        int size = unpacker.unpackMapHeader();
        Map<String, Object> properties = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = unpacker.unpackString();
            Value value = unpacker.unpackValue();
            properties.put(key, convertMsgPackValue(value)); // 调用新的辅助方法
        }
        return properties;
    }

    /**
     * 将 MessagePack Value 对象转换为对应的 Java 对象。
     * @param value MessagePack Value 对象
     * @return 转换后的 Java 对象
     */
    private Object convertMsgPackValue(Value value) {
        return switch (value.getValueType()) {
            case NIL -> null;
            case BOOLEAN -> value.asBooleanValue().getBoolean();
            case INTEGER ->
                // 这里选择 asLong() 以涵盖更大的整数范围
                value.asIntegerValue().asLong();
            case FLOAT ->
                // 这里选择 asDouble() 以保留浮点数的精度
                value.asFloatValue().asNumberValue().toDouble();
            case STRING -> value.asStringValue().asString();
            case BINARY ->
                // 对于二进制数据，转换为 byte[]
                value.asBinaryValue().asByteArray();
            default -> value.toJson(); // Fallback to JSON string for unknown types
        };
    }

    private List<Location> deserializeLocationList(MessageUnpacker unpacker) throws IOException {
        int size = unpacker.unpackArrayHeader();
        List<Location> locations = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Location location = deserializeLocation(unpacker);
            locations.add(location);
        }
        return locations;
    }

    private Location deserializeLocation(MessageUnpacker unpacker) throws IOException {
        unpacker.unpackArrayHeader();
        String appUri = unpacker.unpackString();
        String graphId = unpacker.unpackString();
        String extensionName = unpacker.unpackString();
        return new Location(appUri, graphId, extensionName);
    }
}
