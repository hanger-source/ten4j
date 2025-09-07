package source.hanger.core.path;

import java.util.Objects;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.Location;

/**
 * 对应 C 语言的 `struct ten_path_t`，作为 `PathIn` 和 `PathOut` 的基类，通过组合实现。
 * 严格映射 C 源码字段，包含路径的通用属性和运行时状态。
 */
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
public class PathBase {

    // --- 对应 C 语言 ten_path_t 的核心字段 ---
    @ToString.Include
    private final String commandId; // C: ten_string_t cmd_id;
    @ToString.Include
    private final String parentCommandId; // C: ten_string_t parent_cmd_id;
    @ToString.Include
    private final String commandName; // C: ten_string_t cmd_name;
    @ToString.Include
    private final Location sourceLocation; // C: ten_loc_t src_loc;
    @ToString.Include
    private long expiredTimeUs; // C: uint64_t expired_time_us;

    private final PathTable attachedTable; // C: ten_path_table_t *table;
    private final PathType type; // C: TEN_PATH_TYPE type;

    // --- 新增：对应 C 语言 ten_path_t 中的 PathGroup 相关字段 ---
    // C: ten_shared_ptr_t *group; (在 Java 中直接引用 PathGroup 对象)
    private PathGroup group;

    // C: bool last_in_group;
    private boolean lastInGroup;

    // --- 新增：对应 C 语言 ten_path_t 中的结果缓存和状态字段 ---
    // C: ten_shared_ptr_t *cached_cmd_result; (在 Java 中直接引用 CommandResult 对象)
    private CommandResult cachedCommandResult;

    // C: bool has_received_final_cmd_result;
    private Boolean hasReceivedFinalCommandResult;

    // 构造函数，严格匹配 C 语言 `ten_path_init` 的参数，并添加了 PathGroup 和结果处理相关字段的初始化
    public PathBase(String commandId, String parentCommandId, String commandName, Location sourceLocation,
            long expiredTimeUs, PathTable attachedTable, PathType type) {
        this.commandId = Objects.requireNonNull(commandId, "Command ID must not be null.");
        this.parentCommandId = parentCommandId; // 允许为 null 或空字符串
        this.commandName = Objects.requireNonNull(commandName, "Command name must not be null.");
        this.sourceLocation = Objects.requireNonNull(sourceLocation, "Source location must not be null.");
        this.expiredTimeUs = expiredTimeUs;
        this.attachedTable = Objects.requireNonNull(attachedTable, "Attached PathTable must not be null.");
        this.type = Objects.requireNonNull(type, "Path type must not be null.");

        // PathGroup 和结果处理相关字段的初始化
        this.group = null; // 默认不属于任何组
        this.lastInGroup = false;
        this.cachedCommandResult = null;
        this.hasReceivedFinalCommandResult = false;
    }
}
