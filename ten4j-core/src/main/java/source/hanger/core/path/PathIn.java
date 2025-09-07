package source.hanger.core.path;

import java.util.Objects;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.connection.Connection; // 导入 Connection 类
import source.hanger.core.message.command.Command; // 导入 Command 类
import source.hanger.core.message.Location; // 用于便捷方法返回 Location

/**
 * 对应 C 语言中的 `ten_path_in_t` 结构体，通过组合 `PathBase` 来模拟继承 `ten_path_t`。
 * 用于追踪入站命令的上下文和结果回溯。
 * 严格按照 C 源码 `struct ten_path_in_t` 的核心字段映射。
 *
 * @param base             --- 对应 C 语言 ten_path_in_t 的 `base` 字段 --- C: ten_path_t base;
 * @param originalCommand  Java 端为方便起见，直接存储原始 Command 和 Connection 作为上下文 原始命令实例
 * @param sourceConnection 消息来自的物理连接
 */
@Slf4j
public record PathIn(PathBase base, Command originalCommand, Connection sourceConnection) {

    // --- 对应 C 语言 ten_path_in_t 中的核心字段 ---
    // C: ten_msg_conversion_t *result_conversion; // Java 中可能通过其他机制实现或从上下文获取
    // C: ten_loc_t original_dest_loc; // 原始命令的目的位置，在 PathBase 中已有
    // src_loc，这里指原始请求的目的
    // C: ten_string_t original_cmd_name; // PathBase 中已有 cmd_name

    /**
     * 构造函数。
     *
     * @param base             PathBase 实例，包含通用路径信息。
     * @param originalCommand  原始命令实例，用于获取原始目的位置、名称等信息。
     * @param sourceConnection 消息来自的物理连接，用于结果回溯。
     */
    public PathIn(PathBase base, Command originalCommand, Connection sourceConnection) {
        this.base = Objects.requireNonNull(base, "PathBase must not be null.");
        this.originalCommand = Objects.requireNonNull(originalCommand, "Original command must not be null.");
        this.sourceConnection = sourceConnection; // 允许为 null，例如来自内部调度

        log.debug("PathIn created: commandId={}, parentCommandId={}, name={}, srcLoc={}, expiredTime={}, connection={}",
            base.getCommandId(), base.getParentCommandId(), base.getCommandName(),
            base.getSourceLocation(), base.getExpiredTimeUs(),
            sourceConnection != null ? sourceConnection.getConnectionId() : "N/A");
    }

    // 提供一些便捷方法来访问 base 中的属性，这是 Java 的习惯做法
    public String getCommandId() {
        return base.getCommandId();
    }

    public String getParentCommandId() {
        return base.getParentCommandId();
    }

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

    // 辅助方法，获取原始命令的目的地 (从 originalCommand 中提取)
    public Location getOriginalCommandDestinationLocation() {
        if (originalCommand != null && originalCommand.getDestLocs() != null
            && !originalCommand.getDestLocs().isEmpty()) {
            return originalCommand.getDestLocs().getFirst();
        }
        return null;
    }
}