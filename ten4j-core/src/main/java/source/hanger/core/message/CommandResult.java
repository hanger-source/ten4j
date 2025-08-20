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

/**
 * {@code CommandResult} 类代表了 {@code Command} 命令的执行结果。
 * 它是 Ten Framework 中异步命令处理和状态反馈的核心机制之一。
 * 一个 CommandResult 提供了关于原始命令的 ID、类型、名称、执行状态码，
 * 以及两个关键的布尔标志：{@code isFinal} 和 {@code isCompleted}，
 * 用于精细地管理和理解命令的生命周期和结果产出。
 *
 * <p>主要特点：</p>
 * <ul>
 *   <li><b>关联原始命令:</b> 每个 {@code CommandResult} 都与一个原始的 {@code Command} 关联，
 *     通过 {@code originalCommandId}、{@code originalCmdType} 和 {@code originalCmdName} 字段进行标识。</li>
 *   <li><b>状态码:</b> {@code statusCode} 表示命令执行的宏观结果（成功、失败等）。</li>
 *   <li><b>分层完成状态:</b>
 *     <ul>
 *       <li>{@code isFinal}: 表示当前 CommandResult 是否为该命令的“最终结果之一”。
 *         一个命令在执行过程中可能产生多个最终结果（例如，每个阶段的完成）。</li>
 *       <li>{@code isCompleted}: 表示整个命令的生命周期是否已“圆满结束”，
 *         包括所有后台清理和资源释放。它代表命令的整体终结。</li>
 *     </ul>
 *   </li>
 *   <li><b>灵活的属性扩展:</b> 通过继承 {@code Message} 并使用 {@code properties} Map，
 *     CommandResult 可以携带任意的详细信息、错误消息或业务数据。</li>
 *   <li><b>工厂方法:</b> 提供静态工厂方法（如 {@code success()} 和 {@code fail()}）
 *     简化 CommandResult 实例的创建。</li>
 * </ul>
 *
 * <p>设计目的：</p>
 * 旨在支持复杂的、异步的、分阶段的命令执行场景，提供清晰的状态反馈和生命周期管理，
 * 从而使调用者能够更好地理解命令的进度和最终状态，并进行相应的处理和资源管理。
 */
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

    /**
     * 标记当前 CommandResult 是否为命令的最终结果。
     * 当设置为 true 时，表示此 CommandResult 包含了命令执行的一个最终状态（例如，一个阶段的完成或最终输出）。
     * 但需要注意的是，isFinal = true 并不意味着整个命令的生命周期已经完全结束，
     * 命令可能仍在后台进行清理工作，或等待其他依赖的异步操作完成。
     *
     * <p>可能的场景：</p>
     * <ul>
     *   <li><b>true:</b> 命令的某个阶段已完成并产生了最终输出，或者这是命令的最终结果。</li>
     *   <li><b>false:</b> 当前 CommandResult 只是一个中间结果、进度更新（例如，文件下载进度），
     *                   或者一个心跳消息，命令仍在执行中，后续还会有更多 CommandResult 产生。</li>
     * </ul>
     */
    @JsonProperty("is_final")
    private boolean isFinal;

    /**
     * 标记整个命令的生命周期是否已完全结束。
     * 当设置为 true 时，表示该命令的所有内部和外部相关任务都已完成，
     * 包括所有后台清理工作、资源释放以及所有副作用的处理。
     * 此时，调用者可以完全放心地认为该命令已经完成，无需再保留任何与该命令相关的状态或资源。
     *
     * <p>与 {@code isFinal} 的区别：</p>
     * <ul>
     *   <li>{@code isFinal} 关注的是<b>单个 CommandResult 对象</b>的性质：它是否代表了命令的一个最终状态。</li>
     *   <li>{@code isCompleted} 关注的是<b>整个命令的生命周期</b>：命令是否已经彻底结束，不再有任何后续的活动。</li>
     * </ul>
     * <p>示例：</p>
     * 一个复杂命令可能分多个阶段执行。每个阶段完成时，可能返回一个 {@code isFinal = true} 的 CommandResult，
     * 但只有当所有阶段都完成，并且所有相关的资源和后台任务都清理完毕后，整个命令的 {@code isCompleted} 才为 true。
     * 在简单的同步命令中，{@code isFinal} 为 true 的同时 {@code isCompleted} 也可能立即为 true。
     * 但在更复杂的异步或分布式场景中，它们可能在时间上分离。
     */
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