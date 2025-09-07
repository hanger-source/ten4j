package source.hanger.core.path;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.message.CommandExecutionHandle;
import source.hanger.core.message.CommandResult;

/**
 * 对应 C 语言的 `ten_path_group_t` 结构体，用于管理一组相关的 `PathOut` 实例的命令结果返回策略。
 * 当一个命令产生多个 `PathOut` 实例时（例如发送到多个目的地），它们会被组织成一个 `PathGroup`。
 */
@Slf4j
@Getter
@ToString
public class PathGroup {

    private final String groupId; // 路径组的唯一标识，通常是原始命令的 cmd_id
    private final PathTable attachedTable; // 所属的 PathTable
    private final ResultReturnPolicy policy; // C: TEN_RESULT_RETURN_POLICY policy;
    private final CommandExecutionHandle<CommandResult> commandExecutionHandle; // 逻辑结果处理器

    // C: ten_list_t members; // ten_path_t (这里存储 PathOut，因为它包含了 PathBase)
    // 使用 ConcurrentMap 方便通过 commandId 快速查找组成员
    private final ConcurrentMap<String, PathOut> members;

    public PathGroup(String groupId, PathTable attachedTable, ResultReturnPolicy policy, List<PathOut> initialMembers,
            CommandExecutionHandle<CommandResult> commandExecutionHandle) {
        this.groupId = Objects.requireNonNull(groupId, "Group ID must not be null.");
        this.attachedTable = Objects.requireNonNull(attachedTable, "Attached PathTable must not be null.");
        this.policy = Objects.requireNonNull(policy, "Result return policy must not be null.");
        this.members = new ConcurrentHashMap<>();

        if (initialMembers != null) {
            for (PathOut pathOut : initialMembers) {
                addMember(pathOut);
            }
        }
        this.commandExecutionHandle = Objects.requireNonNull(commandExecutionHandle,
                "CommandExecutionHandle must not be null for PathGroup.");
        log.debug("PathGroup created: groupId={}, policy={}, membersCount={}", groupId, policy, members.size());
    }

    /**
     * 向路径组中添加一个 PathOut 成员。
     *
     * @param pathOut 要添加的 PathOut 实例。
     */
    public void addMember(PathOut pathOut) {
        Objects.requireNonNull(pathOut, "PathOut member must not be null.");
        if (!members.containsKey(pathOut.getCommandId())) {
            members.put(pathOut.getCommandId(), pathOut);
            pathOut.setGroup(this); // 将 PathOut 关联到此 PathGroup
            log.debug("PathGroup {}: Added member: commandId={}", groupId, pathOut.getCommandId());
        } else {
            log.warn("PathGroup {}: Member with commandId {} already exists.", groupId, pathOut.getCommandId());
        }
    }

    /**
     * 从路径组中移除一个 PathOut 成员。
     *
     * @param commandId 要移除的 PathOut 的命令 ID。
     * @return 被移除的 PathOut 实例，如果不存在则返回 null。
     */
    public PathOut removeMember(String commandId) {
        PathOut removed = members.remove(commandId);
        if (removed != null) {
            removed.setGroup(null); // 解除 PathOut 与此 PathGroup 的关联
            log.debug("PathGroup {}: Removed member: commandId={}", groupId, commandId);
        }
        return removed;
    }

    /**
     * 获取路径组中剩余的活跃成员数量。
     *
     * @return 活跃成员数量。
     */
    public int getActiveMembersCount() {
        return members.size();
    }

    /**
     * 检查所有组成员是否都已收到最终结果。
     * 主要用于 EACH_OK_AND_ERROR 策略。
     *
     * @return 如果所有成员都已收到最终结果，则返回 true，否则返回 false。
     */
    public boolean allMembersReceivedFinalResult() {
        for (PathOut member : members.values()) {
            if (!member.hasReceivedFinalResult()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取 PathGroup 中缓存的最终命令结果。
     * 主要用于 FIRST_ERROR_OR_LAST_OK 策略。
     * 在 C 源码中，PathGroup 本身不直接缓存结果，而是依赖 PathOut 的 `cached_cmd_result` 字段。
     * 这里为了方便，可以提供一个方法来聚合或获取最终被选择的 CommandResult。
     *
     * @return 最终的 CommandResult，或者 null 如果尚未确定。
     */
    public CommandResult getFinalCachedCommandResult() {
        // 根据 FIRST_ERROR_OR_LAST_OK 策略，如果存在失败的 PathOut，则返回第一个失败的结果。
        // 否则，返回最后一个成功的 PathOut 的 cachedCommandResult。
        CommandResult errorResult = null;
        CommandResult lastOkResult = null;

        for (PathOut member : members.values()) {
            CommandResult cached = member.getCachedCommandResult();
            if (cached != null) {
                if (!cached.isSuccess()) {
                    return cached; // 发现第一个错误结果，立即返回
                } else {
                    lastOkResult = cached; // 总是更新为最后一个成功的缓存结果
                }
            }
        }
        return lastOkResult;
    }

    /**
     * 检查路径组是否已完成。
     * 如果策略是 FIRST_ERROR_OR_LAST_OK，则当收到一个错误结果，或所有成员都已完成且选择了最后一个成功结果时完成。
     * 如果策略是 EACH_OK_AND_ERROR，则当所有成员都已收到最终结果时完成。
     *
     * @return 如果路径组已完成，则返回 true，否则返回 false。
     */
    public boolean isGroupCompleted() {
        switch (policy) {
            case FIRST_ERROR_OR_LAST_OK:
                // 如果有任何一个成员的 cachedCommandResult 是失败的，且不是最终结果，则组完成
                for (PathOut member : members.values()) {
                    CommandResult cached = member.getCachedCommandResult();
                    if (cached != null && !cached.isSuccess() && !cached.getIsFinal()) { // 注意这里是
                                                                                         // !cached.getIsFinal()，表示非最终的错误直接结束
                        return true;
                    }
                }
                // 如果所有成员都已收到最终结果，并且有 cachedCommandResult，则完成。
                // 这里的逻辑会随着 CommandResult 的处理不断更新 cachedCommandResult 和
                // hasReceivedFinalCommandResult
                // 最终由 PathTable 来判断和触发组的完成。
                // 暂时简化为：如果所有成员都已标记为收到最终结果，则组完成 (这是 EACH_OK_AND_ERROR 的逻辑)
                return allMembersReceivedFinalResult();

            case EACH_OK_AND_ERROR:
                return allMembersReceivedFinalResult();
            default:
                return false; // 未知策略或不应在此判断完成状态
        }
    }
}
