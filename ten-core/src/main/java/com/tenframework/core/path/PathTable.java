package com.tenframework.core.path;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.tenframework.core.engine.MessageSubmitter;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.server.GraphStoppedException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 路径表，负责管理命令和数据在Engine内部的流转路径 (PathOut, PathIn)。
 * 对应C语言中的ten_path_table_t结构，并保持命名一致性。
 * <p>
 * C 端使用 `ten_list_t` (链表) 来管理 `in_paths` 和 `out_paths`。Java 端为了性能上的快速查找，
 * 使用 `ConcurrentMap` 来实现通过 `commandId` 进行的 O(1) 查找，这在语义上是对齐的。
 * </p>
 * 核心功能包括：
 * 1. 创建和管理命令的输出路径 (PathOut)，用于结果回溯。
 * 2. 处理数据消息的输入路径 (PathIn)，存储命令上下文。
 * 3. 根据不同的结果返回策略处理CommandResult。
 * 4. 清理断开连接的Channel相关的路径。
 */
@Slf4j
@Getter
public class PathTable {

    /**
     * 维护 commandId 到 PathOut 的映射，用于命令结果的回溯。
     */
    private final ConcurrentMap<String, PathOut> pathOuts = new ConcurrentHashMap<>(); // 键类型改为 String

    /**
     * 维护 commandId 到 PathIn 的映射，用于入站命令的回溯。
     */
    private final ConcurrentMap<String, PathIn> inPaths = new ConcurrentHashMap<>();

    private final MessageSubmitter messageSubmitter;

    // 对应 C 端 `TEN_PATH_TABLE_ATTACH_TO`
    private final PathTableAttachedTo attachedTo;
    // 对应 C 端 `attached_target` (void *)
    private final Object attachedTarget;

    public PathTable(PathTableAttachedTo attachedTo, Object attachedTarget, MessageSubmitter messageSubmitter) {
        this.attachedTo = attachedTo;
        this.attachedTarget = attachedTarget;
        this.messageSubmitter = messageSubmitter;
    }

    /**
     * 创建一个PathOut实例并添加到路径表中。
     *
     * @param commandId           命令ID
     * @param parentCommandId     父命令ID (可选)
     * @param commandName         命令名称
     * @param sourceLocation      命令源位置
     * @param destinationLocation 命令目标位置
     * @param resultFuture        用于完成命令结果的CompletableFuture
     * @param returnPolicy        结果返回策略
     * @param returnLocation      命令结果回传到的目标位置
     */
    public void createOutPath(String commandId, String parentCommandId, String commandName, Location sourceLocation,
            Location destinationLocation, CompletableFuture<Object> resultFuture,
            ResultReturnPolicy returnPolicy, Location returnLocation) {
        PathOut pathOut = new PathOut(commandId, parentCommandId, commandName, sourceLocation,
                destinationLocation, resultFuture, returnPolicy, returnLocation);
        pathOuts.put(commandId, pathOut); // 直接使用 String commandId 作为键

        log.debug("PathTable: 创建PathOut: commandId={}, parentCommandId={}, source={}, dest={}, returnLoc={}",
                commandId, parentCommandId, sourceLocation, destinationLocation, returnLocation);
    }

    /**
     * 根据Command ID获取PathOut实例。
     *
     * @param commandId 命令ID
     * @return PathOut的Optional，如果不存在则为空
     */
    public Optional<PathOut> getOutPath(String commandId) {
        return Optional.ofNullable(pathOuts.get(commandId)); // 直接使用 String commandId 进行查找
    }

    /**
     * 从路径表中移除PathOut实例。
     *
     * @param commandId 要移除的命令ID
     */
    public void removeOutPath(String commandId) {
        PathOut removedPath = pathOuts.remove(commandId); // 直接使用 String commandId 进行移除
        if (removedPath != null) {
            log.debug("PathTable: 移除PathOut: commandId={}", commandId);
        }
    }

    /**
     * 创建一个PathIn实例并添加到路径表中。
     *
     * @param command 命令实例
     */
    public void createInPath(Command command) {
        // C 端 ten_path_in_create 接收的是 table, cmd_name, parent_cmd_id, cmd_id, src_loc,
        // result_conversion
        // Java 端 PathIn 构造函数接收 commandName, commandId, parentCommandId, sourceLocation
        PathIn pathIn = new PathIn(command.getName(), command.getId(), command.getParentCommandId(),
                command.getSrcLoc());
        inPaths.put(command.getId(), pathIn);
        log.debug("PathTable: 创建PathIn: commandId={}, commandName={}, parentCommandId={}, srcLoc={}",
                command.getId(), command.getName(), command.getParentCommandId(), command.getSrcLoc());
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
     * 处理结果返回策略
     * 根据PathOut中配置的ResultReturnPolicy来决定如何处理命令结果
     */
    public void handleResultReturnPolicy(PathOut pathOut, CommandResult commandResult)
            throws CloneNotSupportedException {
        ResultReturnPolicy policy = pathOut.getReturnPolicy();

        switch (policy) {
            case FIRST_ERROR_OR_LAST_OK -> handleFirstErrorOrLastOkPolicy(pathOut, commandResult);
            case EACH_OK_AND_ERROR -> handleEachOkAndErrorPolicy(pathOut, commandResult);
            default -> {
                log.warn("PathTable: 未知的结果返回策略: policy={}, commandId={}",
                        policy, commandResult.getOriginalCommandId());
                handleFirstErrorOrLastOkPolicy(pathOut, commandResult);
            }
        }
    }

    /**
     * 处理FIRST_ERROR_OR_LAST_OK策略
     * 优先返回第一个错误，或等待所有OK结果并返回最后一个OK结果
     */
    private void handleFirstErrorOrLastOkPolicy(PathOut pathOut, CommandResult commandResult)
            throws CloneNotSupportedException {
        if (!commandResult.isSuccess() && !pathOut.isHasReceivedFinalCommandResult()) {
            log.debug("PathTable: 收到错误结果，立即返回: commandId={}, error={}",
                    commandResult.getId(), commandResult.getErrorMessage());
            completeCommandResult(pathOut, commandResult);
            return;
        }

        if (commandResult.isSuccess()) {
            pathOut.setCachedCommandResult(commandResult);
            log.debug("PathTable: 缓存成功结果: commandId={}",
                    commandResult.getId());
        }

        if (commandResult.isFinal()) {
            log.debug("PathTable: FIRST_ERROR_OR_LAST_OK最终结果. CommandId: {}", commandResult.getId());
            CommandResult finalResult = pathOut.getCachedCommandResult() != null ? pathOut.getCachedCommandResult()
                    : commandResult;
            completeCommandResult(pathOut, finalResult);
        }
    }

    /**
     * 处理EACH_OK_AND_ERROR策略
     * 返回每个OK或ERROR结果（流式结果）
     */
    private void handleEachOkAndErrorPolicy(PathOut pathOut, CommandResult commandResult)
            throws CloneNotSupportedException {
        log.debug("PathTable: 流式返回结果: commandId={}, isSuccess={}, isFinal={}",
                commandResult.getId(), commandResult.isSuccess(), commandResult.isFinal());

        if (commandResult.isFinal()) {
            log.debug("PathTable: EACH_OK_AND_ERROR最终结果. CommandId: {}", commandResult.getId());
            completeCommandResult(pathOut, commandResult);
        }
    }

    /**
     * 完成命令结果的Future并进行回溯
     * 将CommandResult的commandId恢复为parentCommandId，目的地设置为原始命令的sourceLocation
     */
    private void completeCommandResult(PathOut pathOut, CommandResult commandResult) throws CloneNotSupportedException {
        pathOut.setHasReceivedFinalCommandResult(true);

        if (pathOut.getResultFuture() != null && !pathOut.getResultFuture().isDone()) {
            if (commandResult.isSuccess()) {
                pathOut.getResultFuture().complete(commandResult.getPayload());
            } else {
                pathOut.getResultFuture().completeExceptionally(new RuntimeException(
                        "Command execution failed: " + commandResult.getErrorMessage()));
            }
            log.debug("PathTable: 命令结果Future已完成: commandId={}",
                    commandResult.getId());
        }

        if (pathOut.getParentCommandId() != null && !pathOut.getParentCommandId().isEmpty()) { // 检查是否为 null 或空字符串
            CommandResult backtrackResult = commandResult.clone();

            String parentCmdId = pathOut.getParentCommandId();
            backtrackResult.setId(parentCmdId); // 修正为 setId

            backtrackResult.setDestLocs(List.of(pathOut.getSourceLocation()));
            backtrackResult.setSrcLoc(pathOut.getDestinationLocation());

            messageSubmitter.submitMessage(backtrackResult);
            log.debug("PathTable: 命令结果已回溯: originalCommandId={}, parentCommandId={}",
                    commandResult.getId(), pathOut.getParentCommandId());
        }

        removeOutPath(pathOut.getCommandId()); // 确保移除的是正确类型的 ID
    }

    /**
     * 清理与指定图实例相关的所有PathOut实例。
     * 当图实例停止时调用。
     *
     * @param graphId 要清理的图实例ID
     */
    public void cleanupPathsForGraph(String graphId) {
        if (graphId == null || graphId.isEmpty()) {
            log.warn("PathTable: 尝试清理空的或无效的Graph ID相关的路径");
            return;
        }

        log.info("PathTable: 清理与Graph {} 相关的PathOut实例", graphId);

        List<String> commandIdsToCleanup = pathOuts.entrySet().stream()
                .filter(entry -> {
                    PathOut pathOut = entry.getValue();
                    return (pathOut.getSourceLocation() != null
                            && graphId.equals(pathOut.getSourceLocation().getGraphId())) ||
                            (pathOut.getDestinationLocation() != null
                                    && graphId.equals(pathOut.getDestinationLocation().getGraphId()));
                })
                .map(Map.Entry::getKey) // 直接获取 String 类型的键
                .collect(Collectors.toList());

        if (commandIdsToCleanup.isEmpty()) {
            log.debug("PathTable: 没有发现与Graph {} 相关的PathOut需要清理", graphId);
            return;
        }

        log.debug("PathTable: 发现 {} 个与Graph {} 相关的PathOut需要清理", commandIdsToCleanup.size(), graphId);

        for (String commandId : commandIdsToCleanup) {
            Optional<PathOut> pathOutOpt = getOutPath(commandId);
            if (pathOutOpt.isPresent()) {
                PathOut pathOut = pathOutOpt.get();
                if (pathOut.getResultFuture() != null && !pathOut.getResultFuture().isDone()) {
                    pathOut.getResultFuture().completeExceptionally(new GraphStoppedException(
                            "Graph " + graphId + " stopped. CommandResult cannot be returned."));
                }
                removeOutPath(commandId);
                log.debug("PathTable: 清理与停止图相关的PathOut: commandId={}", commandId);
            } else {
                log.warn("PathTable: 在图停止清理时未找到PathOut，可能已被其他机制清理: commandId={}", commandId);
            }
        }
    }
}