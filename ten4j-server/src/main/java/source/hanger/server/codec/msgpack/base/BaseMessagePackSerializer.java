package source.hanger.server.codec.msgpack.base;

import org.msgpack.core.MessagePacker;
import source.hanger.core.message.Message;
import source.hanger.core.message.Location;
import source.hanger.server.codec.msgpack.MessagePackSerializer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 抽象基类，用于提供所有 MessagePack 序列化器的通用结构。
 * 具体的消息类型序列化器将继承此基类。
 * @param <T> 序列化的源对象类型，必须是 Message 的子类
 */
public abstract class BaseMessagePackSerializer<T extends Message> implements MessagePackSerializer<T> {

    // Base Message 类的固定字段数量（不包括 'type' 字段）
    // id, srcLoc, destLocs, name, timestamp, properties (Map)
    protected static final int BASE_MESSAGE_FIELDS_COUNT = 6;


    @Override
    public void serialize(MessagePacker packer, T target) throws IOException {
        // 首先打包 MessagePack 数组头部
        // 数组总长度 = type 字段 (1) + BASE_MESSAGE_FIELDS_COUNT + 子类特有字段数量
        packer.packArrayHeader(getFieldCount());

        // 1. 序列化 type 字段 (第一个字段)
        packer.packString(target.getType().toString());

        // 2. 按照严格的、预定义的顺序打包 Message 的核心字段
        packer.packString(target.getId());
        serializeLocation(packer, target.getSrcLoc());
        serializeLocationList(packer, target.getDestLocs());
        packer.packString(target.getName());
        packer.packLong(target.getTimestamp());
        serializeProperties(packer, target.getProperties());

        // 3. 调用子类的方法来序列化特有字段
        serializeInternal(packer, target);
    }

    /**
     * 子类应该实现此方法来序列化其特有字段。
     * @param packer MessagePacker 实例
     * @param target 序列化的源对象
     * @throws IOException IO 异常
     */
    protected abstract void serializeInternal(MessagePacker packer, T target) throws IOException;

    /**
     * 返回此消息类型（包括基类字段和自身字段）在 MessagePack 数组中占用的字段总数。
     * 此计数用于 MessagePack 数组头部的长度计算。
     *
     * @return 字段总数
     */
    public int getFieldCount() {
        // Message 类的字段总数 = type 字段 (1) + BASE_MESSAGE_FIELDS_COUNT + 子类特有字段数量
        return 1 + BASE_MESSAGE_FIELDS_COUNT + getSpecificFieldCount();
    }

    /**
     * 子类实现此方法，返回其特有字段的数量。
     * @return 特有字段的数量
     */
    protected abstract int getSpecificFieldCount();


    /**
     * 序列化 properties 字段，它是一个 Map<String, Object>。
     * 使用 MessagePack Map Header 进行封装。
     */
    protected void serializeProperties(MessagePacker packer, Map<String, Object> properties) throws IOException {
        if (properties == null || properties.isEmpty()) {
            packer.packMapHeader(0);
            return;
        }
        packer.packMapHeader(properties.size());
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            packer.packString(entry.getKey());
            // 使用辅助方法 packObject 来处理动态类型
            packObject(packer, entry.getValue());
        }
    }

    /**
     * 序列化 Location 列表。
     * 使用 MessagePack Array Header 进行封装。
     */
    protected void serializeLocationList(MessagePacker packer, List<Location> locations) throws IOException {
        if (locations == null || locations.isEmpty()) {
            packer.packArrayHeader(0);
            return;
        }
        packer.packArrayHeader(locations.size());
        for (Location location : locations) {
            serializeLocation(packer, location);
        }
    }

    /**
     * 序列化单个 Location 对象。
     * Location 对象应该作为 MessagePack Array (固定顺序) 序列化。
     */
    protected void serializeLocation(MessagePacker packer, Location location) throws IOException {
        if (location == null) {
            packer.packNil();
            return;
        }
        packer.packArrayHeader(3); // Location 有三个字段：appUri, graphId, extensionName
        packer.packString(Objects.requireNonNullElse(location.getAppUri(), ""));
        packer.packString(Objects.requireNonNullElse(location.getGraphId(), ""));
        packer.packString(Objects.requireNonNullElse(location.getExtensionName(), ""));
    }

    /**
     * 辅助方法，用于根据对象类型进行 MessagePack 序列化。
     * 支持基本类型、字符串、字节数组、List 和 Map。
     * @param packer MessagePacker 实例
     * @param value 待序列化的对象
     * @throws IOException IO 异常
     */
    protected void packObject(MessagePacker packer, Object value) throws IOException {
        switch (value) {
            case null -> packer.packNil();
            case String s -> packer.packString(s);
            case Integer i -> packer.packInt(i);
            case Long l -> packer.packLong(l);
            case Boolean b -> packer.packBoolean(b);
            case Float v -> packer.packFloat(v);
            case Double v -> packer.packDouble(v);
            case byte[] bytes -> {
                packer.packBinaryHeader(bytes.length);
                packer.writePayload(bytes);
            }
            case List<?> list -> {
                packer.packArrayHeader(list.size());
                for (Object item : list) {
                    packObject(packer, item);
                }
            }
            case Map<?, ?> map -> {
                packer.packMapHeader(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    // Map 的键必须是 String
                    packer.packString(entry.getKey().toString());
                    packObject(packer, entry.getValue());
                }
            }
            default ->
                // 对于其他未知类型，可以考虑抛出异常或进行其他处理
                // 为避免运行时错误，这里暂时将其序列化为字符串表示，或者直接抛出异常。
                // 抛出异常可以强制开发者处理未预期的类型。
                throw new IOException(
                    "Unsupported object type for MessagePack serialization: %s".formatted(value.getClass().getName()));
        }
    }
}
