package source.hanger.core.path;

import java.util.Objects;

import lombok.Getter;
import lombok.ToString;
import source.hanger.core.message.CommandExecutionHandle;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.Location;

/**
 * 对应 C 语言的 `ten_path_out_t` 结构体，通过组合 `PathBase` 来模拟继承 `ten_path_t`。
 * 用于追踪出站命令的上下文和结果处理。
 * 严格按照 C 源码 `struct ten_path_out_t` 的核心字段映射。
 *
 * @param base                   --- 对应 C 语言 ten_path_out_t 的 `base` 字段 --- C: ten_path_t base;
 * @param commandExecutionHandle --- 对应 C 语言 ten_path_out_t 的 `result_handler` 字段 --- Java 中使用 CommandExecutionHandle
 *                               接口来封装回调逻辑和数据
 */
public record PathOut(PathBase base, CommandExecutionHandle<CommandResult> commandExecutionHandle) {

    /**
     * 构造函数。
     *
     * @param base                   PathBase 实例，包含通用路径信息和结果缓存/状态。
     * @param commandExecutionHandle 用于处理命令结果的 CommandExecutionHandle 接口。
     */
    public PathOut(PathBase base, CommandExecutionHandle<CommandResult> commandExecutionHandle) {
        this.base = Objects.requireNonNull(base, "PathBase must not be null.");
        this.commandExecutionHandle = commandExecutionHandle; // 允许为 null，表示不需要回调
    }

    // 提供一些便捷方法来访问 base 中的属性，这是 Java 的习惯做法
    @ToString.Include
    public String getCommandId() {
        return base.getCommandId();
    }

    @ToString.Include
    public String getParentCommandId() {
        return base.getParentCommandId();
    }

    @ToString.Include
    public String getCommandName() {
        return base.getCommandName();
    }

    public Location getSourceLocation() {
        return base.getSourceLocation();
    }

    public long getExpiredTimeUs() {
        return base.getExpiredTimeUs();
    }

    public PathTable getAttachedTable() {
        return base.getAttachedTable();
    }

    public PathType getType() {
        return base.getType();
    }

    public PathGroup getGroup() {
        return base.getGroup();
    }

    public boolean isLastInGroup() {
        return base.isLastInGroup();
    }

    public CommandResult getCachedCommandResult() {
        return base.getCachedCommandResult();
    }

    public Boolean hasReceivedFinalResult() {
        return base.getHasReceivedFinalCommandResult();
    } // Changed method name for consistency

    // 设置 PathBase 中的属性 (如果需要)，由于 PathBase 已经有 @Setter，这里是便捷方法
    public void setGroup(PathGroup group) {
        base.setGroup(group);
    }

    public void setLastInGroup(boolean lastInGroup) {
        base.setLastInGroup(lastInGroup);
    }

    public void setCachedCommandResult(CommandResult cachedCommandResult) {
        base.setCachedCommandResult(cachedCommandResult);
    }

    public void setHasReceivedFinalCommandResult(boolean hasReceivedFinalCommandResult) {
        base.setHasReceivedFinalCommandResult(hasReceivedFinalCommandResult);
    }

    public void setExpiredTimeUs(long expiredTimeUs) {
        base.setExpiredTimeUs(expiredTimeUs);
    }
}