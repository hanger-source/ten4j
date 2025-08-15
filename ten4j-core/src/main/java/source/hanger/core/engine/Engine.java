package source.hanger.core.engine;

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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import source.hanger.core.app.App;
import source.hanger.core.app.MessageReceiver;
import source.hanger.core.command.EngineCommandHandler;
import source.hanger.core.command.engine.TimeoutCommandHandler;
import source.hanger.core.command.engine.TimerCommandHandler;
import source.hanger.core.common.StatusCode;
import source.hanger.core.connection.Connection;
import source.hanger.core.graph.GraphDefinition;
import source.hanger.core.graph.NodeDefinition;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.command.Command;
import source.hanger.core.path.PathIn;
import source.hanger.core.path.PathTable;
import source.hanger.core.path.PathTableAttachedTo;
import source.hanger.core.remote.Remote;
import source.hanger.core.runloop.Runloop;
import source.hanger.core.tenenv.TenEnvProxy;

import static source.hanger.core.message.MessageType.CMD_TIMEOUT;
import static source.hanger.core.message.MessageType.CMD_TIMER;

/**
 * `Engine` 类代表 Ten 框架中一个独立的执行单元，负责管理和运行一个 Graph。
 * 它对应 C 语言中的 `ten_engine_t` 结构体。
 */
@Slf4j
@Getter
public class Engine implements Agent, MessageSubmitter, CommandSubmitter,
    MessageReceiver { // Implements CommandSubmitter, MessageReceiver

    private final String graphId;
    private final GraphDefinition graphDefinition; // 引擎所加载的 Graph 的定义
    private final Runloop runloop; // 引擎自身的运行循环
    private final PathTable pathTable; // 消息路由表
    private final EngineExtensionContext engineExtensionContext; // 扩展上下文管理器
    private final ExtensionMessageDispatcher messageDispatcher; // 消息派发器
    private final Map<MessageType, EngineCommandHandler> commandHandlers; // 新增命令处理器映射
    private final ManyToOneConcurrentArrayQueue<QueuedMessage> inMsgs; // 消息输入队列, 类型改为 QueuedMessage
    private final boolean hasOwnLoop; // 是否拥有自己的 Runloop
    private final List<Connection> orphanConnections; // 存储未被 Remote 认领的 Connection
    private final Map<String, Remote> remotes; // 管理 Remote 实例
    private final App app; // 引用所属的 App 实例
    @Getter
    private final TenEnvProxy<EngineEnvImpl> engineEnvProxy; // 新增：Engine 自身的 TenEnvProxy 实例
    private final ConcurrentMap<String, CompletableFuture<CommandResult>> commandFutures;
    // Engine 自身的 CompletableFuture
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
            runloop = Runloop.createRunloopWithWorker("Engine[%s]".formatted(graphId),
                this); // 每个 Engine 都有自己的 Runloop
        } else {
            // 如果没有自己的 Runloop，则尝试使用 App 的 Runloop
            // 确保 app.getAppRunloop() 不为 null，否则这是一个逻辑错误
            if (app.getAppRunloop() == null) {
                throw new IllegalStateException(
                    "Engine %s requires a Runloop, but neither hasOwnLoop is true nor app.getAppRunloop() is available."
                        .formatted(graphId));
            }
            runloop = app.getAppRunloop(); // 使用 App 的 Runloop
        }

        commandFutures = new ConcurrentHashMap<>(); // 确保这里已经初始化

        // 修正 pathTable 的初始化，使用 Engine 自身作为 MessageSubmitter 和 CommandSubmitter
        pathTable = new PathTable(PathTableAttachedTo.ENGINE, this, this); // Update

        // 修正 extensionContext 的初始化，使用 Engine 自身作为 MessageSubmitter 和 CommandSubmitter
        engineExtensionContext = new EngineExtensionContext(this, app, pathTable, this,
            this); // Pass this (Engine) as submitters

        // 初始化消息派发器
        // DefaultExtensionMessageDispatcher 期望 ExtensionContext 和 ConcurrentMap<Long,
        messageDispatcher = new DefaultExtensionMessageDispatcher(engineExtensionContext,
            (ConcurrentMap)commandFutures); // Cast

        inMsgs = new ManyToOneConcurrentArrayQueue<>(Runloop.DEFAULT_INTERNAL_QUEUE_CAPACITY); // 初始化消息输入队列
        orphanConnections = Collections.synchronizedList(new ArrayList<>());
        remotes = new ConcurrentHashMap<>(); // 初始化远程连接映射

        // 初始化 Engine 自身的 TenEnvProxy 实例
        engineEnvProxy = new TenEnvProxy<>(
            runloop,
            new EngineEnvImpl(this, runloop, graphDefinition, app), // 将 graphDefinition 整个传入
            "Engine-%s".formatted(graphId));

        // 注册 Engine 级别的命令处理器
        commandHandlers = new HashMap<>(); // Initialize commandHandlers map here
        commandHandlers.put(CMD_TIMER, new TimerCommandHandler());
        commandHandlers.put(CMD_TIMEOUT, new TimeoutCommandHandler());

        log.info("Engine {} created with hasOwnLoop={}", graphId, hasOwnLoop);
    }

    @Override // 实现 MessageReceiver 接口的方法
    public boolean handleInboundMessage(Message message, Connection connection) {
        // 将来自 MessageReceiver 的消息委托给 Engine 内部的 submitInboundMessage 方法
        return submitInboundMessage(message, connection);
    }

    @Override
    public int doWork() throws Exception {
        // 从输入队列中排水并处理消息
        return inMsgs.drain(message -> {
            processMessage(message.message(), message.connection()); // 传递 message 和 connection
        });
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
        if (graphDefinition.getNodes() != null) { // 修正：遍历 nodes 列表
            for (NodeDefinition node : graphDefinition.getNodes()) { // 修正：遍历 NodeDefinition
                // 只处理类型为 "extension" 的节点
                if ("extension".equalsIgnoreCase(node.getType())) {
                    engineExtensionContext.loadExtension(
                        node.getName(), // 使用 NodeDefinition 的 name
                        node.getAddonName(), // 使用 NodeDefinition 的 addonName
                        node.getExtensionGroupName(),
                        node.getProperty()); // 最后一个参数 ExtInfo 暂时为 null，loadExtension 内部会创建
                } else if ("extension_group".equalsIgnoreCase(node.getType())) {
                    // 对于 ExtensionGroup 类型的节点，也需要加载对应的 ExtensionGroup
                    engineExtensionContext.loadExtensionGroup(
                        node.getName(), // 使用 NodeDefinition 的 name 作为 instance name
                        node.getAddonName(), // 使用 NodeDefinition 的 addonName
                        node.getProperty() // 传递 NodeDefinition 的 property
                    );
                }
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
                future.completeExceptionally(new IllegalStateException("Engine %s stopped.".formatted(graphId)));
            }
        });
        commandFutures.clear();

        // 关闭所有远程连接
        remotes.values().forEach(remote -> remote.shutdown()); // 修正：使用 lambda 表达式
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
     * 路由消息到指定目标 Remote。
     * 与 C 语言的 ten_engine_route_msg_to_remote 对齐。
     *
     * @param message 待路由的消息，必须包含唯一的目的地 Remote URI。
     * @return 一个 CompletableFuture，表示消息发送的结果。
     */
    private CompletableFuture<Void> routeMessageToRemote(Message message) {
        if (message.getDestLocs() == null || message.getDestLocs().size() != 1) {
            log.warn("Engine {}: 消息 {} 没有单一的 Remote 目的地，无法通过 Remote 路由。",
                graphId, message.getId());
            return CompletableFuture.completedFuture(null); // 返回一个已完成的 Future，表示未发送
        }

        String destUri = message.getDestLocs().getFirst().getAppUri();
        if (destUri == null || destUri.isEmpty()) {
            log.warn("Engine {}: 消息 {} 的目的地 Remote URI 为空，无法路由。",
                graphId, message.getId());
            return CompletableFuture.completedFuture(null); // 返回一个已完成的 Future，表示未发送
        }

        Remote remote = remotes.get(destUri);
        if (remote != null) {
            log.debug("Engine {}: 路由消息 {} 到 Remote {}。", graphId, message.getId(), destUri);
            return remote.sendOutboundMessage(message);
        } else {
            log.warn("Engine {}: 找不到目标 Remote {}，消息 {} 无法发送。",
                graphId, destUri, message.getId());
            return CompletableFuture.completedFuture(null); // 返回一个已完成的 Future，表示未发送
        }
    }

    /**
     * 处理 Engine 的入站消息（在 Runloop 线程中调用）。
     *
     * @param message    待处理的消息。
     * @param connection 消息来源的连接，可能为 null。
     */
    public void processMessage(Message message, Connection connection) { // 增加 connection 参数
        if (!isReadyToHandleMsg && !isMessageAllowedWhenClosing(message)) {
            log.warn("Engine {}: 在非活跃状态下收到消息 {} (Type: {})，已忽略。",
                graphId, message.getId(), message.getType());
            // 如果是命令，返回失败结果
            if (message instanceof Command command) {
                submitCommandResult(
                    CommandResult.fail(command.getId(), command.getType(), command.getName(),
                        "Engine not ready to handle messages."));
            }
            return;
        }

        log.debug("Engine {}: 处理消息 {} (Type: {}) 来自连接 {}", graphId, message.getId(), message.getType(),
            connection != null ? connection.getConnectionId() : "N/A"); // 打印连接信息

        if (message instanceof Command command) {
            // 如果是 App 级别或 Engine 级别的命令，由 Engine 自身处理
            processCommand(command);
        } else if (message instanceof CommandResult commandResult) {
            // Engine 接收到 CommandResult 后，需要通过自己的 PathTable 查找原始的 PathIn
            // 从 PathIn 获取 originalMessage (原始 Command) 和 sourceConnection，
            // 然后将 CommandResult 发送回 sourceConnection。
            if (commandResult.getOriginalCommandId() != null && !commandResult.getOriginalCommandId().isEmpty()) {
                Optional<PathIn> pathInOpt = pathTable.getInPath(commandResult.getOriginalCommandId());
                if (pathInOpt.isPresent()) {
                    PathIn pathIn = pathInOpt.get();
                    Connection originalConnection = pathIn.getSourceConnection(); // 获取原始连接

                    if (originalConnection != null) {
                        log.debug("Engine {}: 路由命令结果 {} 到原始连接 {}。", graphId, commandResult.getId(),
                            originalConnection.getConnectionId());
                        originalConnection.sendOutboundMessage(commandResult);
                    } else {
                        log.warn("Engine {}: 原始连接为空，无法路由命令结果 {}。", graphId, commandResult.getId());
                    }
                    pathTable.removeInPath(commandResult.getOriginalCommandId()); // <-- 移除 PathIn
                } else {
                    log.warn("Engine {}: 未找到与命令结果 {} 对应的 PathIn。可能已超时或已被处理。", graphId,
                        commandResult.getOriginalCommandId());
                    // 如果没有 PathIn，尝试按目的地路由，或者如果来自 Remote 且有 sourceConnection，则发送回去
                    if (commandResult.getDestLocs() != null && !commandResult.getDestLocs().isEmpty()) {
                        routeMessageToRemote(commandResult); // 对于没有 PathIn 的结果，尝试按目的地路由
                    } else if (connection != null) { // 如果有来源连接 (例如来自 Extension 的内部结果)，则直接回传给它
                        connection.sendOutboundMessage(commandResult);
                    }
                }
            } else { // 对于没有 originalCommandId 的 CommandResult，或者其他非命令消息
                if (message.getDestLocs() != null && !message.getDestLocs().isEmpty()) {
                    Location firstDest = message.getDestLocs().getFirst();
                    if (graphId.equals(firstDest.getGraphId())) { // 目标是当前 Engine 内部的 Extension
                        messageDispatcher.dispatchMessage(message);
                    } else if (firstDest.getAppUri() != null && !firstDest.getAppUri().isEmpty()) { // 目标是其他 App/Remote
                        routeMessageToRemote(message);
                    } else {
                        log.warn("Engine {}: 消息 {} (Type: {}) 无法路由，目的地 Loc 无效。",
                            graphId, message.getId(), message.getType());
                    }
                } else {
                    log.warn("Engine {}: 消息 {} (Type: {}) 没有目的地，无法处理。",
                        graphId, message.getId(), message.getType());
                }
            }
        } else { // 对于非命令和非命令结果的消息，尝试路由到目标 Extension 或 Remote
            // 对于非命令和非命令结果的消息，如果连接是孤立连接，则将其从孤立列表中移除
            if (connection != null && orphanConnections.contains(connection)) {
                log.info("Engine {}: 收到来自孤立连接 {} 的第一条业务消息，将其从孤立列表中移除。",
                    graphId, connection.getConnectionId());
                removeOrphanConnection(connection);
            }

            Location firstDest = null;
            // 检查消息是否有目的地，并尝试路由
            if (message.getDestLocs() != null && !message.getDestLocs().isEmpty()) {
                firstDest = message.getDestLocs().getFirst();
            }
            if (firstDest == null || graphId.equals(firstDest.getGraphId())) {
                messageDispatcher.dispatchMessage(message);
            } else {
                routeMessageToRemote(message);
            }
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

            if (graphId.equals(destLoc.getGraphId())) { // 目标是当前 Engine
                if (destLoc.getExtensionName() == null) { // 目标是当前 Engine 自身 (非 Extension)
                    EngineCommandHandler handler = commandHandlers.get(command.getType());
                    if (handler != null) {
                        try {
                            handler.handle(engineEnvProxy, command);
                        } catch (Exception e) {
                            log.error("Engine {}: 命令处理器处理命令 {} 失败: {}", graphId, command.getId(),
                                e.getMessage(),
                                e);
                            submitCommandResult(
                                CommandResult.fail(command.getId(), command.getType(), command.getName(),
                                    "Engine command handling failed: %s".formatted(
                                        e.getMessage()))); // Changed to submitCommandResult
                        }
                    } else {
                        // 如果 Engine 没有注册处理器，且命令是针对 App 的，则重新路由回 App
                        // 例如：StopGraphCommand 应该由 App 处理
                        if (app.getAppCommandHandlers().containsKey(command.getType())) { // 检查 App 是否有此命令的处理器
                            log.debug("Engine {}: 将 App 级别的命令 {} 重新路由回 App。", graphId, command.getId());
                            app.handleInboundMessage(command, null); // 重新提交给 App 的队列
                        } else {
                            log.warn("Engine {}: 未知 Engine 级别命令类型或没有注册处理器: {}", graphId,
                                command.getType());
                            submitCommandResult(
                                CommandResult.fail(command.getId(), command.getType(), command.getName(),
                                    "Unknown Engine command type or no handler registered: %s".formatted(
                                        command.getType()))); // Changed
                        }
                    }
                } else { // 目标是当前 Engine 内部的 Extension
                    messageDispatcher.dispatchMessage(command);
                }
            } else if (destLoc.getAppUri() != null && !destLoc.getAppUri().isEmpty() &&
                app.getAppUri().equals(destLoc.getAppUri())) { // 目标是当前 App 但不是当前 Engine
                log.debug("Engine {}: 将指向同一 App 内其他 Engine 的命令 {} 重新路由回 App。", graphId,
                    command.getId());
                app.handleInboundMessage(command, null); // 重新提交给 App 的队列
            } else if (destLoc.getAppUri() != null && !destLoc.getAppUri().isEmpty()) { // 目标是其他 App/Remote
                routeMessageToRemote(command); // 修正：直接通过 Engine 路由到 Remote
            } else {
                // 没有目的地，无法处理
                submitCommandResult(CommandResult.fail(command, "Command has no destination.")); // Changed to
            }
        } else if (command.getDestLocs() == null) {
            messageDispatcher.dispatchMessage(command);
        } else {
            // 没有目的地，无法处理
            log.warn("Engine {}: 命令 {} 没有目的地 Location，无法处理。", graphId, command.getId());
            submitCommandResult(CommandResult.fail(command, "Command has no destination.")); // Changed to
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
     * 当连接绑定到 Remote 或不再是孤立连接时，将其从孤立连接列表中移除。
     *
     * @param connection 要移除的连接实例。
     */
    public void removeOrphanConnection(Connection connection) {
        if (connection != null && orphanConnections.remove(connection)) {
            log.info("Engine {}: 连接 {} 已从孤立连接列表中移除。", graphId, connection.getConnectionId());
        }
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
     * 如果已存在相同 graphId 的 Remote，则返回现有实例；否则创建新实例。
     * 此方法与 C 语言中的 ten_engine_connect_to_graph_remote 对齐。
     *
     * @param targetAppUri      目标 App 的 URI。
     * @param graphId           Graph 的 ID。
     * @param initialConnection 可选的初始连接，如果存在，表示该 Remote 对应一个物理连接。
     * @return 对应的 Remote 实例。
     */
    public Remote getOrCreateRemote(String targetAppUri, String graphId, Connection initialConnection) {
        // 检查是否已存在具有相同 graphId 的 Remote
        Remote existingRemote = remotes.get(targetAppUri); // Remote 的 key 是 targetAppUri
        if (existingRemote != null) {
            log.info("Engine {}: Remote {} 已存在。", graphId, existingRemote.getUri());
            // 如果存在初始连接且该连接是 Engine 的孤立连接，将其链接到现有 Remote。
            // 模拟 C 语言中 ten_engine_link_orphan_connection_to_remote 的行为。
            if (initialConnection != null) {
                if (orphanConnections.contains(initialConnection)) {
                    log.info("Engine {}: 将孤立连接 {} 链接到现有 Remote {}。",
                        graphId, initialConnection.getConnectionId(),
                        targetAppUri);
                    removeOrphanConnection(initialConnection); // 从孤立连接中移除
                    // 这里的 attachToRemote 已在 Remote 构造函数中处理，所以无需再次调用
                    // conn.attachToRemote(existingRemote); // 确保连接依附于此 Remote
                }
            }
            return existingRemote;
        }

        // 如果不存在，则创建新的 Remote
        log.info("Engine {}: 创建新的 Remote: {}", graphId, targetAppUri);

        // 确保传递正确的 runloop
        Runloop remoteRunloop = hasOwnLoop ? runloop : app.getAppRunloop(); // 根据 Engine 是否有自己的 Runloop 来选择

        // 关键：创建 Remote 实例时，initialConnection 也会被传入 Remote 的构造函数
        // Remote 的构造函数将负责调用 connection.attachToRemote(this) 完成依附
        Remote newRemote = new Remote(targetAppUri, initialConnection, this, remoteRunloop);

        // 将新创建的 Remote 添加到 remotes 映射中
        addRemote(newRemote); // <-- 关键修改点

        // 如果存在初始连接且该连接是 Engine 的孤立连接，将其链接到新创建的 Remote。
        // 模拟 C 语言中 ten_engine_link_orphan_connection_to_remote 的行为。
        orphanConnections.remove(initialConnection);
        log.info("Engine {}: 将孤立连接 {} 链接到新创建的 Remote {}。", graphId, initialConnection.getConnectionId(),
            targetAppUri);
        return newRemote;
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
        CompletableFuture<CommandResult> future = commandFutures.remove(originalCommandId);
        if (future != null) {
            if (commandResult.getStatusCode() == StatusCode.OK) { // 修改比较方式
                future.complete(commandResult);
            } else {
                future.completeExceptionally(new RuntimeException(
                    "Command failed with status: %d, Detail: %s".formatted(commandResult.getStatusCode().getValue(),
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
                try {
                    commandFutures.put(command.getId(), future);
                } catch (Throwable throwable) {
                    log.warn("Engine {}: 尝试提交命令 {}，但无法将 CompletableFuture 放入 commandFutures 映射。",
                        graphId, command.getId(), throwable);
                }
                // 提交命令到消息队列，这里因为命令是内部生成，所以 connection 为 null
                submitInboundMessage(command, null); // 更新这里
            });
        } else {
            // 如果已经在 Runloop 线程，则直接执行
            commandFutures.put(command.getId(), future);
            submitInboundMessage(command, null); // 更新这里
        }
        return future;
    }

    public boolean submitInboundMessage(Message message, Connection connection) {
        if (message == null) {
            log.warn("Engine {}: 尝试提交空消息。", graphId);
            return false;
        }

        boolean success = inMsgs.offer(new QueuedMessage(message, connection));
        if (!success) {
            log.warn("Engine {}: 内部消息队列已满，消息 {} 被丢弃。", graphId, message.getId());
            return false;
        }

        // 异步通知 Runloop 线程处理队列中的消息
        runloop.wakeup();
        return true;
    }

    // 新增：向 Engine 添加 Remote 实例
    public void addRemote(Remote remote) {
        if (remote != null) {
            remotes.put(remote.getUri(), remote); // 将 getRemoteUri() 改为 getUri()
            log.info("Engine {}: 添加 Remote: {} (总数: {})", graphId, remote.getUri(),
                remotes.size()); // 将 getRemoteUri() 改为 getUri()
        }
    }

    // 新增：从 Engine 中移除 Remote 实例
    public void removeRemote(String remoteUri) {
        if (remoteUri != null) {
            Remote removedRemote = remotes.remove(remoteUri);
            if (removedRemote != null) {
                log.info("Engine {}: 移除 Remote: {} (剩余: {})", graphId, remoteUri, remotes.size());
            } else {
                log.warn("Engine {}: 尝试移除不存在的 Remote: {}", graphId, remoteUri);
            }
        }
    }

    // 内部类，用于包装消息和其来源连接
    private record QueuedMessage(Message message, Connection connection) {
    }
}