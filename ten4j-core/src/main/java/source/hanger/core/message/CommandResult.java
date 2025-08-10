package source.hanger.core.message;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import source.hanger.core.util.MessageUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CommandResult extends Message implements Cloneable { // 实现 Cloneable

    // Getters for specific properties
    @Getter
    @JsonProperty("original_cmd_id")
    private String originalCommandId;

    @Getter
    @JsonProperty("original_cmd_type")
    private int originalCmdType;

    @Getter
    @JsonProperty("original_cmd_name")
    private String originalCmdName;

    @Getter
    @JsonProperty("status_code")
    private int statusCode;

    @JsonProperty("is_final")
    private boolean isFinal;

    @JsonProperty("is_completed")
    private boolean isCompleted;

    // 兼容 Lombok @NoArgsConstructor 的全参构造函数（为了Jackson）
    // 实际内部创建时使用自定义构造函数
    public CommandResult(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp,
            String originalCommandId, int originalCmdType, String originalCmdName, int statusCode,
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
    public CommandResult(Location srcLoc, List<Location> destLocs, String originalCommandId, int statusCode,
            String detail) {
        super(MessageUtils.generateUniqueId(), MessageType.CMD_RESULT, srcLoc, destLocs); // 自动生成 id
        this.originalCommandId = originalCommandId;
        originalCmdType = MessageType.CMD_RESULT.ordinal(); // 简化，实际可能需要原始命令的 type
        originalCmdName = originalCommandId; // 原始命令的 ID 作为名称
        this.statusCode = statusCode;
        isFinal = true; // 假设结果是最终的
        isCompleted = true; // 假设结果是完成的

        // detail 放入 properties map
        if (detail != null) {
            getProperties().put("detail", detail);
        }
    }

    public static CommandResult success(String originalCommandId, String detail) {
        // 使用 MessageUtils.generateUniqueId() 生成 id，并提供默认的 srcLoc 和 destLocs
        return new CommandResult(new Location(), Collections.emptyList(), originalCommandId, 0, detail);
    }

    public static CommandResult fail(String originalCommandId, String errorMessage) {
        // 使用 MessageUtils.generateUniqueId() 生成 id，并提供默认的 srcLoc 和 destLocs
        return new CommandResult(new Location(), Collections.emptyList(), originalCommandId, -1, errorMessage);
    }

    // 新增：判断命令是否成功
    public boolean isSuccess() {
        return statusCode == 0;
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
        return (CommandResult)super.clone();
    }

    public boolean isFinal() {
        return isFinal;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    // 辅助方法：获取 detail (从 properties map 中获取)
    public String getDetail() {
        if (getProperties() != null && getProperties().containsKey("detail")) {
            return (String) getProperties().get("detail");
        }
        return null;
    }
}