package source.hanger.core.path;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import source.hanger.core.connection.Connection;
import source.hanger.core.engine.Engine;
import source.hanger.core.engine.MessageSubmitter;
import source.hanger.core.message.CommandExecutionHandle;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.Location;
import source.hanger.core.message.command.Command;
import source.hanger.core.server.GraphStoppedException;

/**
 * 路径表，负责管理命令和数据在Engine内部的流转路径 (PathOut, PathIn)。
 * 对应C语言中的ten_path_table_t结构，并保持命名一致性。
 */
@Slf4j
@Getter
public class PathTable {

    private final ConcurrentMap<String, PathOut> pathOuts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PathIn> inPaths = new ConcurrentHashMap<>();

    // --- 移除：用于存储 PathOut 处理的临时状态，已被 PathGroup 和 PathOut 内部状态取代 ---
    // private final ConcurrentMap<String, PathOutProcessingState>
    // pathOutProcessingStates = new ConcurrentHashMap<>();

    // --- 新增：用于管理 PathGroup 实例 ---
    private final ConcurrentMap<String, PathGroup> pathGroups = new ConcurrentHashMap<>();

    private final MessageSubmitter messageSubmitter;

    private final PathTableAttachedTo attachedTo;
    private final Object attachedTarget;

    public PathTable(PathTableAttachedTo attachedTo, Object attachedTarget, MessageSubmitter messageSubmitter) {
        this.attachedTo = attachedTo;
        this.attachedTarget = attachedTarget;
        this.messageSubmitter = messageSubmitter;
    }

    /**
     * 创建一个PathOut实例并添加到路径表中。
     *
     * @param command     原始命令实例，用于提取信息
     * @param handle      用于处理命令结果的 CommandExecutionHandle
     * @param group       可选的 PathGroup，如果此 PathOut 属于一个组
     * @param lastInGroup 是否是 PathGroup 中的最后一个 PathOut
     */
    public PathOut createOutPath(Command command, CommandExecutionHandle<CommandResult> handle, PathGroup group,
            boolean lastInGroup) {

        String commandId = command.getId();
        String parentCommandId = command.getParentCommandId();
        String originalCommandName = command.getName();
        Location sourceLocation = command.getSrcLoc();

        long expiredTimeUs = System.currentTimeMillis() + getDefaultPathTimeoutDurationUs(PathType.OUT);

        // 创建 PathBase 实例，严格遵循 C ten_path_t 字段
        PathBase pathBase = new PathBase(
                commandId,
                parentCommandId,
                originalCommandName,
                sourceLocation,
                expiredTimeUs,
                this, // 传入当前 PathTable 实例
                PathType.OUT // 传入路径类型
        );

        // 设置 PathGroup 和 lastInGroup 字段
        pathBase.setGroup(group);
        pathBase.setLastInGroup(lastInGroup);

        // 使用 PathBase 和 CommandExecutionHandle 创建 PathOut
        PathOut pathOut = new PathOut(
                pathBase,
                handle);
        pathOuts.put(commandId, pathOut);

        log.debug(
                "PathTable: 创建PathOut: commandId={}, parentCommandId={}, name={}, srcLoc={}, expiredTime={}, group={}, lastInGroup={}",
                commandId, parentCommandId, originalCommandName, sourceLocation, expiredTimeUs,
                (group != null ? group.getGroupId() : "N/A"), lastInGroup);
        return pathOut;
    }

    /**
     * 根据Command ID获取PathOut实例。
     *
     * @param commandId 命令ID
     * @return PathOut的Optional，如果不存在则为空
     */
    public Optional<PathOut> getOutPath(String commandId) {
        return Optional.ofNullable(pathOuts.get(commandId));
    }

    /**
     * 从路径表中移除PathOut实例。
     *
     * @param commandId 要移除的命令ID
     */
    public void removeOutPath(String commandId) {
        PathOut removedPath = pathOuts.remove(commandId);
        if (removedPath != null) {
            log.debug("PathTable: 移除PathOut: commandId={}", commandId);
        }
    }

    /**
     * 创建一个PathIn实例并添加到路径表中。
     *
     * @param command    命令实例
     * @param connection 消息来自的物理连接
     */
    public void createInPath(Command command, Connection connection) {
        long expiredTimeUs = System.currentTimeMillis() + getDefaultPathTimeoutDurationUs(PathType.IN);

        PathBase pathBase = new PathBase(
                command.getId(),
                command.getParentCommandId(),
                command.getName(),
                command.getSrcLoc(),
                expiredTimeUs,
                this, // 传入当前 PathTable 实例
                PathType.IN);

        PathIn pathIn = new PathIn(pathBase, command, connection); // 调用 PathIn 的新构造函数
        inPaths.put(command.getId(), pathIn);
        log.debug("PathTable: 创建PathIn: commandId={}, commandName={}, parentCommandId={}, srcLoc={}, connection={}",
                command.getId(), command.getName(), command.getParentCommandId(), command.getSrcLoc(),
                connection != null ? connection.getConnectionId() : "N/A");
    }

    /**
     * 根据Command ID获取PathIn实例。
     *
     * @param commandId 命令ID
     * @return PathIn的Optional，如果不存在则为空
     */
    public Optional<PathIn> getInPath(String commandId) {
        return Optional.ofNullable(inPaths.get(commandId));
    }

    /**
     * 从路径表中移除PathIn实例。
     *
     * @param commandId 要移除的数据路径ID
     */
    public void removeInPath(String commandId) {
        PathIn removedPath = inPaths.remove(commandId);
        if (removedPath != null) {
            log.debug("PathTable: 移除PathIn: commandId={}", commandId);
        }
    }

    /**
     * 创建一个 PathGroup 实例并添加到路径表中。
     *
     * @param groupId                路径组的 ID，通常是原始命令的 commandId。
     * @param pathOuts               属于此组的所有 PathOut 实例。
     * @param policy                 结果返回策略。
     * @param commandExecutionHandle 关联的命令执行句柄，用于处理整个组的逻辑结果。
     * @return 创建的 PathGroup 实例。
     */
    public PathGroup createPathGroup(String groupId, List<PathOut> pathOuts, ResultReturnPolicy policy,
            CommandExecutionHandle<CommandResult> commandExecutionHandle) {
        PathGroup group = new PathGroup(groupId, this, policy, pathOuts, commandExecutionHandle);
        pathGroups.put(groupId, group);
        log.debug("PathTable: 创建PathGroup: groupId={}, policy={}, membersCount={}", groupId, policy, pathOuts.size());
        return group;
    }

    /**
     * 从路径表中移除 PathGroup 及其所有成员。
     *
     * @param groupId 路径组的 ID。
     */
    public void removePathGroupAndAllItsPaths(String groupId) {
        PathGroup group = pathGroups.remove(groupId);
        if (group != null) {
            log.debug("PathTable: 移除PathGroup: groupId={}, membersCount={}", groupId, group.getMembers().size());
            for (PathOut pathOut : group.getMembers().values()) {
                removeOutPath(pathOut.getCommandId()); // 移除组内的所有 PathOut
            }
        }
    }

    /**
     * 处理结果返回策略。
     * 对应 C 语言 `ten_path_table_process_cmd_result` 的核心逻辑。
     * 这里的核心是调用 CommandExecutionHandle 的 submit/close/closeExceptionally 方法。
     *
     * @param pathOut       传入的 PathOut 实例。
     * @param commandResult 传入的命令结果。
     */
    public boolean handleResultReturnPolicy(PathOut pathOut, CommandResult commandResult)
            throws CloneNotSupportedException {
        CommandExecutionHandle<CommandResult> handle = pathOut.commandExecutionHandle();

        if (handle == null) {
            log.warn("PathTable: PathOut {} 没有关联 CommandExecutionHandle，无法处理结果。", pathOut.getCommandId());
            return false; // 不进行处理
        }

        // 首先，将结果提交到 CommandExecutionHandle 的流中 (无论策略如何)
        handle.submit(commandResult);

        // 获取 PathGroup (如果存在)
        PathGroup group = pathOut.getGroup();

        if (group != null) {
            // --- 属于 PathGroup 的情况 (多目的地/多结果协调) ---
            ResultReturnPolicy policy = group.getPolicy();
            return switch (policy) {
                case FIRST_ERROR_OR_LAST_OK -> handleGroupFirstErrorOrLastOkPolicy(pathOut, commandResult,
                        group);
                case EACH_OK_AND_ERROR -> handleGroupEachOkAndErrorPolicy(pathOut, commandResult, group);
            };
        } else {
            // --- 不属于 PathGroup 的情况 (单个 PathOut 的处理) ---
            return handleSinglePathOutPolicy(pathOut, commandResult, handle);
        }
    }

    private boolean handleSinglePathOutPolicy(PathOut pathOut, CommandResult commandResult,
            CommandExecutionHandle<CommandResult> handle)
            throws CloneNotSupportedException {
        // 在这里，PathOut 的 cachedCommandResult 和 hasReceivedFinalCommandResult 字段将被直接使用
        // 模拟 C 源码中 `ten_path_t` 的这些字段

        if (!commandResult.isSuccess() && !pathOut.hasReceivedFinalResult()) {
            log.debug("PathTable: 单个PathOut收到错误结果，立即完成: commandId={}, error={}",
                    commandResult.getId(), commandResult.getErrorMessage());
            completeCommandResult(pathOut, commandResult, handle);
            pathOut.setHasReceivedFinalCommandResult(true); // 标记已收到最终结果
            removeOutPath(pathOut.getCommandId()); // 移除 PathOut
            return true;
        }

        if (commandResult.isSuccess()) {
            pathOut.setCachedCommandResult(commandResult);
            log.debug("PathTable: 单个PathOut缓存成功结果: commandId={}",
                    commandResult.getId());
        }

        if (commandResult.getIsFinal()) {
            log.debug("PathTable: 单个PathOut最终结果. CommandId: {}", commandResult.getOriginalCommandId());
            CommandResult finalResult = pathOut.getCachedCommandResult() != null ? pathOut.getCachedCommandResult()
                    : commandResult;
            completeCommandResult(pathOut, finalResult, handle);
            pathOut.setHasReceivedFinalCommandResult(true); // 标记已收到最终结果
            removeOutPath(pathOut.getCommandId()); // 移除 PathOut
            return true;
        }
        return false;
    }

    private boolean handleGroupFirstErrorOrLastOkPolicy(PathOut pathOut, CommandResult commandResult, PathGroup group)
            throws CloneNotSupportedException {

        if (!commandResult.isSuccess()) {
            log.debug("PathTable: PathGroup {} 收到错误结果，立即完成整个组: commandId={}, error={}",
                    group.getGroupId(), commandResult.getId(), commandResult.getErrorMessage());
            // 整个组都应该完成，所有 PathOut 的 handle 都应关闭
            completeGroupAndRemove(group, commandResult);
            return true;
        } else {
            // C 源码中，PathGroup 的 FIRST_ERROR_OR_LAST_OK 策略依赖于 PathOut 的 last_in_group 标志和
            // cached_cmd_result
            pathOut.setCachedCommandResult(commandResult);
            log.debug("PathTable: PathGroup {} 缓存成功结果: commandId={}", group.getGroupId(), commandResult.getId());

            if (pathOut.isLastInGroup() && commandResult.getIsFinal()) { // 只有组内最后一个 path 收到最终结果才触发完成
                log.debug("PathTable: PathGroup {} 最后一个 PathOut 收到最终成功结果. CommandId: {}",
                        group.getGroupId(), commandResult.getId());
                // 查找组内是否有错误结果，如果有则返回第一个错误，否则返回最后一个成功的缓存结果
                CommandResult finalGroupResult = group.getFinalCachedCommandResult();
                if (finalGroupResult == null) {
                    finalGroupResult = commandResult; // 以防万一，如果没有缓存的成功结果，则使用当前结果
                }
                completeGroupAndRemove(group, finalGroupResult);
                return true;
            }
        }
        return false;
    }

    private boolean handleGroupEachOkAndErrorPolicy(PathOut pathOut, CommandResult commandResult, PathGroup group)
            throws CloneNotSupportedException {
        log.debug("PathTable: PathGroup {} 流式返回结果: commandId={}, isSuccess={}, isFinal={}",
                group.getGroupId(), commandResult.getId(), commandResult.isSuccess(), commandResult.getIsFinal());

        if (commandResult.getIsFinal()) {
            pathOut.setHasReceivedFinalCommandResult(true); // 标记 PathOut 已收到最终结果
            log.debug("PathTable: PathGroup {} PathOut {} 收到最终结果. isFinal={}",
                    group.getGroupId(), pathOut.getCommandId(), commandResult.getIsFinal());
            if (group.allMembersReceivedFinalResult()) { // 检查组内所有成员是否都已收到最终结果
                log.debug("PathTable: PathGroup {} 所有成员都已收到最终结果，完成整个组。", group.getGroupId());
                completeGroupAndRemove(group, commandResult); // 以当前结果作为最终结果关闭组 (通常是最后一个成员的结果)
                return true;
            }
        }
        return false;
    }

    /**
     * 完成一个 PathOut 的 CommandExecutionHandle 并进行回溯。
     *
     * @param pathOut       要完成的 PathOut 实例。
     * @param commandResult 最终的命令结果。
     * @param handle        关联的 CommandExecutionHandle。
     */
    private void completeCommandResult(PathOut pathOut, CommandResult commandResult,
            CommandExecutionHandle<CommandResult> handle) {
        if (commandResult.isSuccess()) {
            handle.close();
        } else {
            handle.closeExceptionally(new RuntimeException(
                    "Command execution failed: %s".formatted(commandResult.getErrorMessage())));
        }
        log.debug("PathTable: CommandExecutionHandle {} 已完成/异常关闭。", pathOut.getCommandId());

        // 回溯逻辑保持不变，回溯目的地是原始命令的源位置
        if (StringUtils.isNotEmpty(pathOut.getParentCommandId())) {
            Location backtrackDestLoc = pathOut.getSourceLocation();

            CommandResult backtrackResult = commandResult.toBuilder()
                    .originalCommandId(pathOut.getCommandId())
                    .srcLoc(pathOut.getSourceLocation())
                    .destLocs(backtrackDestLoc != null ? List.of(backtrackDestLoc) : null)
                    .build();

            messageSubmitter.submitInboundMessage(backtrackResult, null);
            log.debug("PathTable: 命令结果已回溯: originalCommandId={}, parentCommandId={}, backtrackDestLoc={}",
                    commandResult.getId(), pathOut.getParentCommandId(), backtrackDestLoc);
        }
    }

    /**
     * 完成一个 PathGroup 并移除其所有 PathOut 成员。
     *
     * @param group       要完成的 PathGroup 实例。
     * @param finalResult 最终的命令结果，用于关闭组内所有 CommandExecutionHandle。
     */
    private void completeGroupAndRemove(PathGroup group, CommandResult finalResult) throws CloneNotSupportedException {
        CommandExecutionHandle<CommandResult> groupHandle = group.getCommandExecutionHandle();
        if (groupHandle != null) {
            if (finalResult.isSuccess()) {
                groupHandle.close();
            } else if (finalResult.isFailed() ){
                groupHandle.closeExceptionally(new RuntimeException(
                        "Group command execution failed: %s".formatted(finalResult.getErrorMessage())));
            }
            log.debug("PathTable: PathGroup {} 关联的 CommandExecutionHandle 已完成/异常关闭。", group.getGroupId());
        } else {
            log.warn("PathTable: PathGroup {} 没有关联 CommandExecutionHandle，无法完成组结果。", group.getGroupId());
        }

        // 确保所有 PathOut 都标记为完成，并从 PathTable 中移除
        for (PathOut pathOut : group.getMembers().values()) {
            pathOut.setHasReceivedFinalCommandResult(true); // 确保所有 PathOut 都标记为完成
        }
        removePathGroupAndAllItsPaths(group.getGroupId());
    }

    /**
     * 清理与指定图实例相关的所有PathOut实例和PathGroup。
     * 当图实例停止时调用。
     *
     * @param graphId 要清理的图实例ID
     */
    public void cleanupPathsForGraph(String graphId) {
        if (StringUtils.isEmpty(graphId)) {
            log.warn("PathTable: 尝试清理空的或无效的Graph ID相关的路径");
            return;
        }

        log.info("PathTable: 清理与Graph {} 相关的PathOut实例和PathGroup", graphId);

        // 清理 PathOuts
        List<String> commandIdsToCleanup = pathOuts.entrySet().stream()
                .filter(entry -> {
                    PathOut pathOut = entry.getValue();
                    return pathOut.getSourceLocation() != null
                            && graphId.equals(pathOut.getSourceLocation().getGraphId());
                })
                .map(Map.Entry::getKey)
                .toList();

        for (String commandId : commandIdsToCleanup) {
            Optional<PathOut> pathOutOpt = getOutPath(commandId);
            if (pathOutOpt.isPresent()) {
                PathOut pathOut = pathOutOpt.get();
                CommandExecutionHandle<CommandResult> handle = pathOut.commandExecutionHandle();
                if (handle != null && !handle.toCompletedFuture().isDone()) {
                    handle.closeExceptionally(new GraphStoppedException(
                            "Graph %s stopped. CommandResult cannot be returned.".formatted(graphId)));
                }
                removeOutPath(commandId);
                // 不需要从 pathOutProcessingStates 移除，因为它已不存在
                log.debug("PathTable: 清理与停止图相关的PathOut: commandId={}", commandId);
            } else {
                log.warn("PathTable: 在图停止清理时未找到PathOut，可能已被其他机制清理: commandId={}", commandId);
            }
        }

        // 清理 PathGroups
        List<String> groupIdsToCleanup = pathGroups.entrySet().stream()
                .filter(entry -> {
                    PathGroup group = entry.getValue();
                    return group.getAttachedTable().getAttachedTo() == PathTableAttachedTo.ENGINE
                            && ((Engine) group.getAttachedTable().getAttachedTarget()).getGraphId()
                                    .equals(graphId); // Corrected to use Engine class and getGraphId()
                })
                .map(Map.Entry::getKey)
                .toList();

        for (String groupId : groupIdsToCleanup) {
            removePathGroupAndAllItsPaths(groupId);
        }
    }

    /**
     * 辅助方法：获取 Path 的默认超时时长（微秒）。
     * 在 C 代码中，这个值会根据 PathType (IN/OUT) 从 PathTable 中获取。
     */
    private long getDefaultPathTimeoutDurationUs(PathType pathType) {
        if (pathType == PathType.OUT) {
            return 3 * 60 * 1000 * 1000L; // 3 分钟 (微秒)
        }
        return 5 * 60 * 1000 * 1000L; // 默认 5 分钟
    }
}