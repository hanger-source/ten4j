package source.hanger.core.message;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import source.hanger.core.common.StatusCode;
import source.hanger.core.message.command.Command;
import source.hanger.core.util.MessageUtils;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CommandResult extends Message implements Cloneable { // 实现 Cloneable

    // Getters for specific properties
    @JsonProperty("original_cmd_id")
    private String originalCommandId;

    @JsonProperty("original_cmd_type")
    private MessageType originalCmdType; // 修改为 MessageType 类型

    @JsonProperty("original_cmd_name")
    private String originalCmdName;

    @JsonProperty("status_code")
    private StatusCode statusCode; // 修改为 StatusCode 枚举类型

    @JsonProperty("is_final")
    private boolean isFinal;

    @JsonProperty("is_completed")
    private boolean isCompleted;

    // 兼容 Lombok @NoArgsConstructor 的全参构造函数（为了Jackson）
    // 实际内部创建时使用自定义构造函数
    public CommandResult(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp,
            String originalCommandId, MessageType originalCmdType, String originalCmdName, StatusCode statusCode, // 修改类型
            boolean isFinal, boolean isCompleted) {
        super(id, type, srcLoc, destLocs, null, properties, timestamp); // 传入 null 作为 name
        this.originalCommandId = originalCommandId;
        this.originalCmdType = originalCmdType;
        this.originalCmdName = originalCmdName;
        this.statusCode = statusCode;
        this.isFinal = isFinal;
        this.isCompleted = isCompleted;
    }

    // 用于内部创建的简化构造函数，匹配新的 Message 基类构造
    // 此构造函数将不再对外直接暴露，而是通过静态工厂方法调用
    private CommandResult(String originalCommandId, MessageType originalCmdType, String originalCmdName,
            StatusCode statusCode, String detailOrError) {
        super(MessageUtils.generateUniqueId(), MessageType.CMD_RESULT, new Location(), Collections.emptyList()); // 自动生成
        // id，默认
        // srcLoc
        // 和
        // destLocs
        this.originalCommandId = originalCommandId;
        this.originalCmdType = originalCmdType;
        this.originalCmdName = originalCmdName;
        this.statusCode = statusCode;
        this.isFinal = true; // 假设结果是最终的
        this.isCompleted = true; // 假设结果是完成的

        // 根据状态码，将 detailOrError 放入不同的 properties 键中
        if (detailOrError != null) {
            if (statusCode == StatusCode.OK) {
                getProperties().put("detail", detailOrError);
            } else if (statusCode == StatusCode.ERROR) {
                getProperties().put("error_message", detailOrError); // 错误消息放入 error_message 键
            } else {
                // 对于其他状态码，默认放入 detail，或者根据需要处理
                getProperties().put("detail", detailOrError);
            }
        }
    }

    /**
     * 从一个原始命令创建 CommandResult 的基础工厂方法。
     * 用于内部简化其他静态方法的调用。
     *
     * @param originalCommand 原始命令对象。
     * @param statusCode      结果状态码。
     * @param detailOrError   详细信息或错误消息。
     * @return 新的 CommandResult 实例。
     */
    private static CommandResult fromCommand(Command originalCommand, StatusCode statusCode, String detailOrError) {
        Objects.requireNonNull(originalCommand, "Original command cannot be null.");
        return new CommandResult(
                originalCommand.getId(),
                originalCommand.getType(), // 原始命令的类型
                originalCommand.getName(), // 原始命令的名称
                statusCode,
                detailOrError);
    }

    public static CommandResult success(String originalCommandId, MessageType originalCmdType, String originalCmdName,
            String detail) {
        return new CommandResult(originalCommandId, originalCmdType, originalCmdName, StatusCode.OK, detail); // 使用
        // StatusCode.OK
    }

    // 重载的 success 方法，从 Command 对象构建
    public static CommandResult success(Command originalCommand, String detail) {
        return fromCommand(originalCommand, StatusCode.OK, detail); // 使用 StatusCode.OK
    }

    // 新增：支持 properties 的 success 方法
    public static CommandResult success(String originalCommandId, MessageType originalCmdType, String originalCmdName,
            String detail, Map<String, Object> properties) {
        CommandResult result = new CommandResult(originalCommandId, originalCmdType, originalCmdName, StatusCode.OK,
                detail);
        if (properties != null) {
            result.getProperties().putAll(properties);
        }
        return result;
    }

    // 新增：支持 properties 的 success 方法，从 Command 对象构建
    public static CommandResult success(Command originalCommand, String detail, Map<String, Object> properties) {
        CommandResult result = fromCommand(originalCommand, StatusCode.OK, detail);
        if (properties != null) {
            result.getProperties().putAll(properties);
        }
        return result;
    }

    public static CommandResult fail(String originalCommandId, MessageType originalCmdType, String originalCmdName,
            String errorMessage) {
        return new CommandResult(originalCommandId, originalCmdType, originalCmdName, StatusCode.ERROR,
                errorMessage); // 使用
        // StatusCode.ERROR
    }

    // 重载的 fail 方法，从 Command 对象构建
    public static CommandResult fail(Command originalCommand, String errorMessage) {
        return fromCommand(originalCommand, StatusCode.ERROR, errorMessage); // 使用 StatusCode.ERROR
    }

    // 新增：判断命令是否成功
    public boolean isSuccess() {
        return statusCode == StatusCode.OK; // 修改比较方式
    }

    // 新增：获取错误信息 (如果存在)
    public String getErrorMessage() {
        if (getProperties() != null && getProperties().containsKey("error_message")) {
            return (String) getProperties().get("error_message");
        }
        return null;
    }

    // 新增：获取 payload (从 properties 中获取)
    public Object getPayload() {
        if (getProperties() != null && getProperties().containsKey("payload")) {
            return getProperties().get("payload");
        }
        return null;
    }

    // 重写 clone 方法以支持深拷贝或浅拷贝（取决于需求），这里提供浅拷贝示例
    @Override
    public CommandResult clone() throws CloneNotSupportedException {
        // 对于引用类型字段，如果需要深拷贝，则在此处进行
        // 例如：cloned.setSrcLoc(this.getSrcLoc().clone());
        // 如果 properties 需要深拷贝，也在此处处理
        return (CommandResult) super.clone();
    }

    // 辅助方法：获取 detail (从 properties map 中获取)
    public String getDetail() {
        if (getProperties() != null && getProperties().containsKey("detail")) {
            return (String) getProperties().get("detail");
        }
        return null;
    }
}