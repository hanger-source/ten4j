package com.tenframework.core.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.tenframework.core.app.App;
import com.tenframework.core.command.EngineCommandHandler;
import com.tenframework.core.command.engine.TimeoutCommandHandler;
import com.tenframework.core.command.engine.TimerCommandHandler;
import com.tenframework.core.connection.Connection;
import com.tenframework.core.graph.ExtensionInfo;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.path.PathTable;
import com.tenframework.core.path.PathTableAttachedTo;
import com.tenframework.core.remote.DummyRemote;
import com.tenframework.core.remote.Remote;
import com.tenframework.core.runloop.Runloop;
import com.tenframework.core.tenenv.TenEnvProxy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

import static com.tenframework.core.message.MessageType.CMD_TIMEOUT;
import static com.tenframework.core.message.MessageType.CMD_TIMER;

/**
 * `Engine` 类代表 Ten 框架中一个独立的执行单元，负责管理和运行一个 Graph。
 * 它对应 C 语言中的 `ten_engine_t` 结构体。
 */
@Slf4j
@Getter
public class Engine implements Agent, MessageSubmitter, CommandSubmitter { // Implements CommandSubmitter

    private final String graphId;
    private final GraphDefinition graphDefinition; // 引擎所加载的 Graph 的定义
    private final Runloop runloop; // 引擎自身的运行循环
    private final PathTable pathTable; // 消息路由表
    private final EngineExtensionContext engineExtensionContext; // 扩展上下文管理器
    private final ExtensionMessageDispatcher messageDispatcher; // 消息派发器
    private final Map<MessageType, EngineCommandHandler> commandHandlers; // 新增命令处理器映射
    private final ManyToOneConcurrentArrayQueue<Message> inMsgs; // 消息输入队列
    private final boolean hasOwnLoop; // 是否拥有自己的 Runloop
    private final List<Connection> orphanConnections; // 存储未被 Remote 认领的 Connection
    private final Map<String, Remote> remotes; // 管理 Remote 实例
    private final App app; // 引用所属的 App 实例
    /**
     * -- GETTER --
     * 获取 Engine 自身的 TenEnvProxy 实例。
     *
     * @return Engine 的 TenEnvProxy 实例。
     */
    @Getter
    private final TenEnvProxy<EngineEnvImpl> engineEnvProxy; // 新增：Engine 自身的 TenEnvProxy 实例
    private final ConcurrentMap<Long, CompletableFuture<CommandResult>> commandFutures; // Engine 自身的 CompletableFuture
    // 映射
    private volatile boolean isReadyToHandleMsg = false;
    private volatile boolean isClosing = false;

    public Engine(String graphId, GraphDefinition graphDefinition, App app, boolean hasOwnLoop) {
        this.graphId = Objects.requireNonNull(graphId, "Graph ID must not be null.");
        this.graphDefinition = Objects.requireNonNull(graphDefinition, "Graph definition must not be null.");
        this.app = Objects.requireNonNull(app, "App must not be null.");
        this.hasOwnLoop = hasOwnLoop; // 在构造函数开头初始化

        // Engine 自身的 Runloop 初始化
        if (hasOwnLoop) {
            runloop = Runloop.createRunloopWithWorker("%s-runloop".formatted(graphId), this); // 每个 Engine 都有自己的 Runloop
        } else { // 如果没有自己的 Runloop，则尝试使用 App 的 Runloop
            // 确保 app.getAppRunloop() 不为 null，否则这是一个逻辑错误
            if (app.getAppRunloop() == null) {
                throw new IllegalStateException(
                    "Engine %s requires a Runloop, but neither hasOwnLoop is true nor app.getAppRunloop() is available."
                        .formatted(graphId));
            }
            runloop = app.getAppRunloop(); // 使用 App 的 Runloop
        }

        // 移除不必要的初始化，TenEnvProxy 已经处理了底层 TenEnv 的概念
        // loopRunner = new LoopRunner(this);

        commandFutures = new ConcurrentHashMap<>(); // 确保这里已经初始化

        // 修正 pathTable 的初始化，使用 Engine 自身作为 MessageSubmitter 和 CommandSubmitter
        pathTable = new PathTable(PathTableAttachedTo.ENGINE, this, this); // Update

        // 修正 extensionContext 的初始化，使用 Engine 自身作为 MessageSubmitter 和 CommandSubmitter
        engineExtensionContext = new EngineExtensionContext(this, app, pathTable, this,
            this); // Pass this (Engine) as submitters

        // 初始化消息派发器
        // DefaultExtensionMessageDispatcher 期望 ExtensionContext 和 ConcurrentMap<Long,
        // CompletableFuture<Object>>
        // 这里需要传递 commandFutures，并处理泛型兼容性问题
        messageDispatcher = new DefaultExtensionMessageDispatcher(engineExtensionContext,
            (ConcurrentMap)commandFutures); // Cast
        // to
        // raw
        // type
        // for
        // now

        inMsgs = new ManyToOneConcurrentArrayQueue<>(Runloop.DEFAULT_INTERNAL_QUEUE_CAPACITY); // 初始化消息输入队列
        orphanConnections = Collections.synchronizedList(new ArrayList<>());
        remotes = new ConcurrentHashMap<>(); // 初始化远程连接映射

        // 初始化 Engine 自身的 TenEnvProxy 实例
        engineEnvProxy = new TenEnvProxy<>(runloop,
            new EngineEnvImpl(this, runloop, graphDefinition.getProperties(), app), // Modified parameters to match
            // EngineEnvImpl constructor
            "Engine-%s".formatted(graphId));

        // 注册 Engine 级别的命令处理器
        commandHandlers = new HashMap<>(); // Initialize commandHandlers map here
        commandHandlers.put(CMD_TIMER, new TimerCommandHandler());
        commandHandlers.put(CMD_TIMEOUT, new TimeoutCommandHandler());

        log.info("Engine {} created with hasOwnLoop={}", graphId, hasOwnLoop);
    }

    // Moved registerCommandHandlers content to constructor to initialize final
    // commandHandlers
    // private void registerCommandHandlers() {
    // // 注册所有 Engine 级别的命令处理器
    // commandHandlers = new HashMap<>(); // Initialize commandHandlers map
    // commandHandlers.put(CMD_TIMER, new TimerCommandHandler());
    // commandHandlers.put(CMD_TIMEOUT, new TimeoutCommandHandler());
    // }

    @Override
    public int doWork() throws Exception {
        // 从输入队列中排水并处理消息
        return inMsgs.drain(this::processMessage);
    }

    @Override
    public String roleName() {
        return "Engine-%s".formatted(graphId);
    }

    /**
     * 启动 Engine。
     */
    public void start() {
        log.info("Engine {}: 启动中...", graphId);
        runloop.start(); // 启动 Engine 的 Runloop

        // 启动 Graph 中的所有 Extension (通过 ExtensionContext)
        // 遍历 GraphDefinition 中的 ExtensionInfo 并加载
        if (graphDefinition.getExtensions() != null) {
            for (ExtensionInfo extInfo : graphDefinition.getExtensions()) {
                // 加载 Extension
                engineExtensionContext.loadExtension(extInfo.getLoc().getExtensionName(),
                    extInfo.getExtensionAddonName(),
                    graphDefinition.getProperties(), runloop, extInfo); // Pass graphDefinition.getProperties() as
                // config
            }
        }
        isReadyToHandleMsg = true;
        log.info("Engine {}: 已启动。", graphId);
    }

    /**
     * 停止 Engine。
     * 调用该方法后，Engine 将不再处理新的入站消息，并尝试停止所有活跃的 Extension 和 Runloop。
     */
    public void stop() {
        log.info("Engine {}: 停止中...", graphId);
        isClosing = true;

        // 停止所有 Extension
        engineExtensionContext.unloadAllExtensions(); // Call unloadAllExtensions instead of cleanup

        // 清理所有命令的 CompletableFuture
        commandFutures.values().forEach(future -> {
            if (!future.isDone()) {
                future.completeExceptionally(new IllegalStateException("Engine " + graphId + " stopped."));
            }
        });
        commandFutures.clear();

        // 关闭所有远程连接
        remotes.values().forEach(Remote::shutdown);
        remotes.clear();

        // 清理孤立连接
        orphanConnections.clear();

        // 清理消息队列
        inMsgs.clear();

        // 停止 Engine 的 Runloop
        if (hasOwnLoop) {
            runloop.shutdown();
        } else {
            // 如果使用 App 的 Runloop，则不应由 Engine 关闭
            log.info("Engine {}: 使用 App 的 Runloop，不关闭 Runloop。", graphId);
        }

        // 关闭 Engine 的 TenEnvProxy
        if (engineEnvProxy != null) {
            engineEnvProxy.close();
        }

        pathTable.cleanupPathsForGraph(graphId); // 清理与此 Engine 相关的 PathTable 路径
        isReadyToHandleMsg = false;
        log.info("Engine {}: 已停止。", graphId);
    }

    /**
     * 处理 Engine 的入站消息（在 Runloop 线程中调用）。
     *
     * @param message 待处理的消息。
     */
    public void processMessage(Message message) {
        if (!isReadyToHandleMsg && !isMessageAllowedWhenClosing(message)) {
            log.warn("Engine {}: 在非活跃状态下收到消息 {} (Type: {})，已忽略。",
                graphId, message.getId(), message.getType());
            // 如果是命令，返回失败结果
            if (message instanceof Command command) {
                submitCommandResult(
                    CommandResult.fail(command.getId(), "Engine not ready to handle messages.")); // Changed
                // to
                // submitCommandResult
            }
            return;
        }

        log.debug("Engine {}: 处理消息 {} (Type: {})", graphId, message.getId(), message.getType());

        if (message instanceof Command command) {
            // 处理命令
            processCommand(command);
        } else if (message instanceof CommandResult) {
            // 处理命令结果
            submitCommandResult((CommandResult)message); // Changed to submitCommandResult
        } else {
            // 其他消息类型派发给 ExtensionContext
            messageDispatcher.dispatchOtherMessage(message);
        }
    }

    /**
     * 处理入站命令。
     *
     * @param command 待处理的命令。
     */
    private void processCommand(Command command) {
        // 如果是 App 级别或 Engine 级别的命令，由 Engine 自身处理
        if (command.getDestLocs() != null && !command.getDestLocs().isEmpty()) {
            Location destLoc = command.getDestLocs().getFirst(); // 假设只处理第一个目的地

            if (graphId.equals(destLoc.getGraphId()) && destLoc.getExtensionName() == null) {
                // 目标是当前 Engine 自身
                EngineCommandHandler handler = commandHandlers.get(command.getType());
                if (handler != null) {
                    try {
                        handler.handle(engineEnvProxy, command);
                    } catch (Exception e) {
                        log.error("Engine {}: 命令处理器处理命令 {} 失败: {}", graphId, command.getId(), e.getMessage(),
                            e);
                        submitCommandResult(CommandResult.fail(command.getId(),
                            "Engine command handling failed: %s".formatted(
                                e.getMessage()))); // Changed to submitCommandResult
                    }
                } else {
                    log.warn("Engine {}: 未知 Engine 级别命令类型或没有注册处理器: {}", graphId, command.getType());
                    submitCommandResult(CommandResult.fail(command.getId(),
                        "Unknown Engine command type or no handler registered: %s".formatted(
                            command.getType()))); // Changed
                    // to
                    // submitCommandResult
                }
            } else if (graphId.equals(destLoc.getGraphId())) {
                // 目标是当前 Engine 内部的 Extension
                engineExtensionContext.dispatchCommandToExtension(command, destLoc.getExtensionName());
            } else {
                // 目标是其他 Engine 或 App，应由 App 路由
                app.sendMessageToLocation(command, null); // 委托给 App 路由
            }
        } else {
            // 没有目的地，无法处理
            log.warn("Engine {}: 命令 {} 没有目的地 Location，无法处理。", graphId, command.getId());
            submitCommandResult(CommandResult.fail(command.getId(), "Command has no destination.")); // Changed to
            // submitCommandResult
        }
    }

    /**
     * 判断消息在 Engine 关闭时是否允许处理（例如命令结果或错误消息）。
     *
     * @param message 消息。
     * @return true 如果允许，否则 false。
     */
    private boolean isMessageAllowedWhenClosing(Message message) {
        // 在关闭过程中，只允许处理命令结果，以确保异步命令的 CompletableFuture 能够完成
        return message instanceof CommandResult;
    }

    /**
     * 添加一个孤立连接。
     *
     * @param connection 要添加的连接。
     */
    public void addOrphanConnection(Connection connection) {
        orphanConnections.add(connection);
        log.info("Engine {}: 添加孤立连接: {}", graphId, connection.getRemoteAddress());
    }

    /**
     * 根据连接 ID 查找孤立连接。
     *
     * @param connId 连接 ID。
     * @return 对应的 Connection，如果不存在则为 Optional.empty()。
     */
    public Optional<Connection> findOrphanConnectionById(String connId) {
        return orphanConnections.stream()
                .filter(conn -> conn.getConnectionId().equals(connId))
                .findFirst();
    }

    /**
     * 获取或创建一个 Remote 实例。
     *
     * @param targetAppUri      目标 App 的 URI。
     * @param targetGraphId     目标 Graph 的 ID。
     * @param initialConnection 初始连接 (可选)。
     * @return 对应的 Remote 实例。
     */
    public Optional<Remote> getOrCreateRemote(String targetAppUri, String targetGraphId,
            Optional<Connection> initialConnection) {
        // TODO: 实现 Remote 的实际创建逻辑
        // 暂时返回 DummyRemote
        return Optional.of(new DummyRemote(targetAppUri, new Location(targetAppUri, targetGraphId, null), this,
            initialConnection));
    }

    /**
     * 处理传入的命令结果消息。
     * 这是 Engine Runloop 线程中的核心处理逻辑。
     *
     * @param commandResult 传入的命令结果。
     */
    @Override
    public void submitCommandResult(CommandResult commandResult) { // Renamed from routeCommandResult
        // 确保在 Engine 的 Runloop 线程中执行
        if (runloop.isNotCurrentThread()) {
            runloop.postTask(() -> submitCommandResult(commandResult)); // Changed to submitCommandResult
            return;
        }

        // 如果命令结果有原始命令 ID，则完成对应的 CompletableFuture
        String originalCommandId = commandResult.getOriginalCommandId();
        // 这里的 CompletableFuture<Object> 应该与 C 端 ten_cmd_t 预期返回的类型对齐
        // 而不是固定为 CommandResult
        CompletableFuture<CommandResult> future = commandFutures.remove(Long.parseLong(originalCommandId));
        if (future != null) {
            if (commandResult.getStatusCode() == 0) {
                future.complete(commandResult);
            } else {
                future.completeExceptionally(new RuntimeException(
                    "Command failed with status: %d, Detail: %s".formatted(commandResult.getStatusCode(),
                        commandResult.getDetail())));
            }
        } else {
            log.warn("Engine {}: 未找到与命令结果 {} 对应的 Future。", graphId, commandResult.getOriginalCommandId());
        }

        // 如果命令结果有返回地址，可能需要向上路由或发送给 Remote
        if (commandResult.getDestLocs() != null && !commandResult.getDestLocs().isEmpty()) {
            // 路由到目标 Engine 或 App (通过 App 的路由机制)
            app.sendMessageToLocation(commandResult, null);
        }
    }

    /**
     * 从 Extension 路由命令结果到 Engine。
     *
     * @param commandResult       命令结果。
     * @param sourceExtensionName 来源 Extension 的名称。
     */
    public void routeCommandResultFromExtension(CommandResult commandResult, String sourceExtensionName) {
        log.debug("Engine {}: Extension {} 路由命令结果 {} 到 Engine。", graphId, sourceExtensionName,
            commandResult.getId());
        // 委托给 Engine 处理，Engine 知道如何路由结果
        submitCommandResult(commandResult); // Changed to submitCommandResult
    }

    /**
     * 提交一个命令到 Engine，并返回一个 CompletableFuture 来跟踪其结果。
     * 该方法是线程安全的，会将命令提交到 Engine 的 Runloop 线程进行处理。
     *
     * @param command 要提交的命令。
     * @return 一个 CompletableFuture，当命令处理完成并返回结果时，它将被完成。
     */
    @Override
    public CompletableFuture<CommandResult> submitCommand(Command command) {
        CompletableFuture<CommandResult> future = new CompletableFuture<>();
        // 确保在 Engine 的 Runloop 线程中执行
        if (runloop.isNotCurrentThread()) {
            runloop.postTask(() -> {
                // 将 CompletableFuture 放入 commandFutures 映射
                commandFutures.put(Long.parseLong(command.getId()), future);
                // 提交命令到消息队列
                submitMessage(command); // 使用已有的 submitMessage
            });
        } else {
            // 如果已经在 Runloop 线程，则直接执行
            commandFutures.put(Long.parseLong(command.getId()), future);
            submitMessage(command); // 使用已有的 submitMessage
        }
        return future;
    }

    @Override
    public boolean submitMessage(Message message) {
        if (message == null) {
            log.warn("Engine {}: 尝试提交空消息。", graphId);
            return false;
        }

        boolean success = inMsgs.offer(message);
        if (!success) {
            log.warn("Engine {}: 内部消息队列已满，消息 {} 被丢弃。", graphId, message.getId());
            return false;
        }

        // 异步通知 Runloop 线程处理队列中的消息
        runloop.wakeup();
        return true;
    }

}