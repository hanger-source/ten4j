package source.hanger.core.message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.msgpack.value.ImmutableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import source.hanger.core.util.IdGenerator;

/**
 * `Message` 是 Ten 框架中所有通信数据的基础抽象类。
 * 它定义了消息的通用结构，包括唯一ID、类型、源位置、目的位置以及可选的名称和属性。
 * 通过 `@JsonTypeInfo` 和 `@JsonSubTypes` 实现多态序列化和反序列化。
 */
//@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
//@JsonSubTypes({
//    @JsonSubTypes.Type(value = GenericCommand.class, name = "CMD"), // 通用命令类型
//    @JsonSubTypes.Type(value = CommandResult.class, name = "CMD_RESULT"), // 命令结果
//    @JsonSubTypes.Type(value = DataMessage.class, name = "DATA"), // 数据消息
//    @JsonSubTypes.Type(value = VideoFrameMessage.class, name = "VIDEO_FRAME"), // 视频帧
//    @JsonSubTypes.Type(value = AudioFrameMessage.class, name = "AUDIO_FRAME"), // 音频帧
//    // 具体命令类型，这些命令的名称需要在 Command 类中单独指定
//    @JsonSubTypes.Type(value = CloseAppCommand.class, name = "CMD_CLOSE_APP"),
//    @JsonSubTypes.Type(value = StartGraphCommand.class, name = "CMD_START_GRAPH"),
//    @JsonSubTypes.Type(value = StopGraphCommand.class, name = "CMD_STOP_GRAPH"),
//    @JsonSubTypes.Type(value = TimerCommand.class, name = "CMD_TIMER"),
//    @JsonSubTypes.Type(value = TimeoutCommand.class, name = "CMD_TIMEOUT")
//})
//@JsonIgnoreProperties(ignoreUnknown = true) // 忽略 JSON 中不存在于类中的字段
@Getter
@Setter
@Accessors(chain = true) // 启用链式设置器
@ToString // 排除 data 字段，因为它可能很大
@SuperBuilder(toBuilder = true)
public abstract class Message implements Cloneable {
    private static final Logger log = LoggerFactory.getLogger(Message.class); // 实现 Cloneable

    //@JsonProperty("id") // 消息的唯一标识符
    private String id;

    //@JsonProperty("src_loc") // 消息的源位置
    private Location srcLoc;

    //@JsonProperty("dest_locs") // 消息的目的位置列表
    private List<Location> destLocs;

    //@JsonProperty("name") // 消息名称 (可选，用于路由或语义)
    private String name;

    //@JsonProperty("timestamp") // 时间戳
    private Long timestamp;

    //@JsonProperty("properties") // 消息的附加属性 (可选)f
    @Singular
    private Map<String, Object> properties;

    @SuppressWarnings("unchecked")
    public static <T extends MessageBuilder<?, ?>> T defaultMessage(T builder) {
        return (T)builder.id(IdGenerator.generateShortId())
            .timestamp(System.currentTimeMillis());
    }

    public abstract MessageType getType();

    public Message(String id, MessageType type, Location srcLoc, List<Location> destLocs, String name,
        Map<String, Object> properties, long timestamp) {
        this.id = id;
        this.srcLoc = srcLoc;
        this.destLocs = destLocs;
        this.name = name;
        this.properties = properties != null ? properties : new HashMap<>();
        this.timestamp = timestamp;
    }

    public Message(String id, MessageType type, Location srcLoc, List<Location> destLocs) {
        this(id, type, srcLoc, destLocs, null, null, System.currentTimeMillis());
    }

    // 重写 clone 方法，支持浅拷贝
    @Override
    public Message clone() throws CloneNotSupportedException {
        return (Message)super.clone();
    }

    public Optional<Integer> getPropertyInteger(String propertyName) {
        Object value = properties.get(propertyName);
        if (value instanceof ImmutableValue immutableValue) {
            if (immutableValue.isNilValue()) {
                return Optional.empty();
            }
            else if (immutableValue.isIntegerValue()) {
                return Optional.of(immutableValue.asIntegerValue().toInt());
            } else {
                log.warn("无法将值转换为Integer propertyName={} type={}", propertyName, immutableValue.getValueType());
            }
        }
        if (value instanceof Integer) {
            return Optional.of((Integer)value);
        }
        return Optional.empty();
    }

    public Optional<Long> getPropertyLong(String propertyName) {
        Object value = properties.get(propertyName);
        if (value instanceof ImmutableValue immutableValue) {
            if (immutableValue.isNilValue()) {
                return Optional.empty();
            }
            else if (immutableValue.isIntegerValue()) {
                return Optional.of(immutableValue.asIntegerValue().toLong());
            } else {
                log.warn("无法将值转换为Long propertyName={} type={}", propertyName, immutableValue.getValueType());
            }
        }
        if (value instanceof Long) {
            return Optional.of((Long)value);
        } else if (value instanceof Integer) { // 兼容 Integer
            return Optional.of(((Integer)value).longValue());
        }
        return Optional.empty();
    }

    public Optional<String> getPropertyString(String propertyName) {
        Object value = properties.get(propertyName);
        if (value instanceof ImmutableValue immutableValue) {
            if (immutableValue.isNilValue()) {
                return Optional.empty();
            }
            else if (immutableValue.isStringValue()) {
                return Optional.of(immutableValue.asStringValue().asString());
            } else {
                log.warn("无法将值转换为String propertyName={} type={}", propertyName, immutableValue.getValueType());
            }
        }
        if (value instanceof String) {
            return Optional.of((String)value);
        }
        return Optional.empty();
    }

    public Optional<Boolean> getPropertyBoolean(String propertyName) {
        Object value = properties.get(propertyName);
        if (value instanceof ImmutableValue immutableValue) {
            if (immutableValue.isNilValue()) {
                return Optional.empty();
            }
            else if (immutableValue.isBooleanValue()) {
                return Optional.of(immutableValue.asBooleanValue().getBoolean());
            } else {
                log.warn("无法将值转换为Boolean propertyName={} type={}", propertyName, immutableValue.getValueType());
            }
        }
        if (value instanceof Boolean) {
            return Optional.of((Boolean)value);
        }
        return Optional.empty();
    }


    public MessageBuilder<?, ?> cloneBuilder() {
        return innerToBuilder()
            .id(IdGenerator.generateShortId())
            .timestamp(System.currentTimeMillis());
    }

    protected abstract MessageBuilder<?, ?> innerToBuilder();
}