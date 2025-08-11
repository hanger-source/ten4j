package source.hanger.core.message;

// Force recompile marker

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import source.hanger.core.message.command.CloseAppCommand;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.StartGraphCommand;
import source.hanger.core.message.command.StopGraphCommand;
import source.hanger.core.message.command.TimeoutCommand;
import source.hanger.core.message.command.TimerCommand;
import source.hanger.core.util.MessageUtils;

/**
 * `Message` 是 Ten 框架中所有通信数据的基础抽象类。
 * 它定义了消息的通用结构，包括唯一ID、类型、源位置、目的位置以及可选的名称和属性。
 * 通过 `@JsonTypeInfo` 和 `@JsonSubTypes` 实现多态序列化和反序列化。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Command.class, name = "CMD"), // 通用命令类型
    @JsonSubTypes.Type(value = CommandResult.class, name = "CMD_RESULT"), // 命令结果
    @JsonSubTypes.Type(value = DataMessage.class, name = "DATA"), // 数据消息
    @JsonSubTypes.Type(value = VideoFrameMessage.class, name = "VIDEO_FRAME"), // 视频帧
    @JsonSubTypes.Type(value = AudioFrameMessage.class, name = "AUDIO_FRAME"), // 音频帧
    // 具体命令类型，这些命令的名称需要在 Command 类中单独指定
    @JsonSubTypes.Type(value = CloseAppCommand.class, name = "CMD_CLOSE_APP"),
    @JsonSubTypes.Type(value = StartGraphCommand.class, name = "CMD_START_GRAPH"),
    @JsonSubTypes.Type(value = StopGraphCommand.class, name = "CMD_STOP_GRAPH"),
    @JsonSubTypes.Type(value = TimerCommand.class, name = "CMD_TIMER"),
    @JsonSubTypes.Type(value = TimeoutCommand.class, name = "CMD_TIMEOUT")
})
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略 JSON 中不存在于类中的字段
@Getter
@Setter
@Accessors(chain = true) // 启用链式设置器
@ToString // 排除 data 字段，因为它可能很大
public abstract class Message implements Cloneable { // 实现 Cloneable

    @JsonProperty("id") // 消息的唯一标识符
    private String id;

    @JsonProperty("type") // 消息类型
    @JsonFormat(shape = JsonFormat.Shape.STRING) // 明确指定按字符串反序列化
    private MessageType type;

    @JsonProperty("src_loc") // 消息的源位置
    private Location srcLoc;

    @JsonProperty("dest_locs") // 消息的目的位置列表
    private List<Location> destLocs;

    @JsonProperty("name") // 消息名称 (可选，用于路由或语义)
    private String name;

    @JsonProperty("properties") // 消息的附加属性 (可选)f
    private Map<String, Object> properties;

    @JsonProperty("timestamp") // 时间戳
    private long timestamp;

    // 构造函数
    public Message() {
        this.id = MessageUtils.generateUniqueId();
        this.timestamp = System.currentTimeMillis();
    }

    public Message(String id, MessageType type, Location srcLoc, List<Location> destLocs, String name,
        Map<String, Object> properties, long timestamp) {
        this.id = id;
        this.type = type;
        this.srcLoc = srcLoc;
        this.destLocs = destLocs;
        this.name = name;
        this.properties = properties != null ? properties : new HashMap<>();
        this.timestamp = timestamp;
    }

    // Overloaded constructors to match previous usage
    public Message(String id, MessageType type, Location srcLoc, List<Location> destLocs, String name,
        Map<String, Object> properties) {
        this(id, type, srcLoc, destLocs, name, properties, System.currentTimeMillis());
    }

    public Message(String id, MessageType type, Location srcLoc, List<Location> destLocs,
        Map<String, Object> properties) {
        this(id, type, srcLoc, destLocs, null, properties, System.currentTimeMillis());
    }

    public Message(String id, MessageType type, Location srcLoc, List<Location> destLocs, String name) {
        this(id, type, srcLoc, destLocs, name, null, System.currentTimeMillis());
    }

    public Message(String id, MessageType type, Location srcLoc, List<Location> destLocs) {
        this(id, type, srcLoc, destLocs, null, null, System.currentTimeMillis());
    }

    // 重写 clone 方法，支持浅拷贝
    @Override
    public Message clone() throws CloneNotSupportedException {
        return (Message)super.clone();
    }
    // endregion

    // region 属性访问方法

    public Optional<Object> getProperty(String path) {
        // TODO: 实现对嵌套路径的支持
        return Optional.ofNullable(properties.get(path));
    }

    public void setProperty(String path, Object value) {
        // TODO: 实现对嵌套路径的支持
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(path, value);
    }

    public boolean hasProperty(String path) {
        return properties != null && properties.containsKey(path);
    }

    public void deleteProperty(String path) {
        if (properties != null) {
            properties.remove(path);
        }
    }

    public Optional<Integer> getPropertyInt(String path) {
        Object value = properties.get(path);
        if (value instanceof Integer) {
            return Optional.of((Integer)value);
        }
        return Optional.empty();
    }

    public void setPropertyInt(String path, int value) {
        setProperty(path, value);
    }

    public Optional<Long> getPropertyLong(String path) {
        Object value = properties.get(path);
        if (value instanceof Long) {
            return Optional.of((Long)value);
        } else if (value instanceof Integer) { // 兼容 Integer
            return Optional.of(((Integer)value).longValue());
        }
        return Optional.empty();
    }

    public void setPropertyLong(String path, long value) {
        setProperty(path, value);
    }

    public Optional<String> getPropertyString(String path) {
        Object value = properties.get(path);
        if (value instanceof String) {
            return Optional.of((String)value);
        }
        return Optional.empty();
    }

    public void setPropertyString(String path, String value) {
        setProperty(path, value);
    }

    public Optional<Boolean> getPropertyBool(String path) {
        Object value = properties.get(path);
        if (value instanceof Boolean) {
            return Optional.of((Boolean)value);
        }
        return Optional.empty();
    }

    public void setPropertyBool(String path, boolean value) {
        setProperty(path, value);
    }

    public Optional<Double> getPropertyDouble(String path) {
        Object value = properties.get(path);
        if (value instanceof Double) {
            return Optional.of((Double)value);
        } else if (value instanceof Float) { // 兼容 Float
            return Optional.of(((Float)value).doubleValue());
        } else if (value instanceof Integer) { // 兼容 Integer
            return Optional.of(((Integer)value).doubleValue());
        } else if (value instanceof Long) { // 兼容 Long
            return Optional.of(((Long)value).doubleValue());
        }
        return Optional.empty();
    }

    public void setPropertyDouble(String path, double value) {
        setProperty(path, value);
    }

    public Optional<Float> getPropertyFloat(String path) {
        Object value = properties.get(path);
        if (value instanceof Float) {
            return Optional.of((Float)value);
        } else if (value instanceof Double) { // 兼容 Double
            return Optional.of(((Double)value).floatValue());
        }
        return Optional.empty();
    }

    public void setPropertyFloat(String path, float value) {
        setProperty(path, value);
    }

    // endregion
}