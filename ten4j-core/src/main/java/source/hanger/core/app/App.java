package source.hanger.core.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import source.hanger.core.command.app.AppCommandHandler;
import source.hanger.core.command.app.CloseAppCommandHandler;
import source.hanger.core.command.app.StartGraphCommandHandler;
import source.hanger.core.command.app.StopGraphCommandHandler;
import source.hanger.core.connection.Connection;
import source.hanger.core.engine.Engine;
import source.hanger.core.extension.Extension;
import source.hanger.core.graph.GraphConfig;
import source.hanger.core.graph.PredefinedGraphEntry;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.StartGraphCommand;
import source.hanger.core.path.PathTable;
import source.hanger.core.path.PathTableAttachedTo; // 导入 PathTableAttachedTo
import source.hanger.core.path.PathIn; // 导入 PathIn
import source.hanger.core.remote.Remote;
import source.hanger.core.runloop.Runloop;
import source.hanger.core.tenenv.TenEnvProxy;
import source.hanger.core.util.MessageUtils;

/**
 * App 类作为 Ten 框架的顶层容器和协调器。
 * 它管理 Engine 实例，处理传入的连接，并路由消息。
 * 对应 C 语言中的 ten_app_t 结构体。
 */
@Slf4j
@Getter
public class App implements Agent, MessageReceiver { // 修正：添加 MessageReceiver 接口

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Map<String, Engine> engines; // 管理所有活跃的 Engine 实例，key 为 graphId
    private final List<Connection> orphanConnections; // 尚未绑定到 Engine 的连接
    private final PathTable pathTable; // App 级别 PathTable - 恢复 App 级别 PathTable
    private final Map<String, Remote> remotes; // 外部远程连接 (跨 App/Engine 通信)
    private final boolean hasOwnRunloopPerEngine; // 每个 Engine 是否有自己的 Runloop
    // 新增：Extension 注册机制
    private final Map<String, Class<? extends Extension>> availableExtensions;
    private final String appUri;
    private final Runloop appRunloop;
    // 新增：App 级别的命令处理器映射
    private final Map<MessageType, AppCommandHandler> appCommandHandlers;
    private final ManyToOneConcurrentArrayQueue<QueuedMessage> inMsgs; // 新增：App 的消息输入队列
    /**
     * -- GETTER --
     * 获取 App 自身的 TenEnvProxy 实例。
     *
     * @return App 的 TenEnvProxy 实例。
     */
    @Getter
    private final TenEnvProxy<AppEnvImpl> appEnvProxy; // 新增：App 自身的 TenEnvProxy 实例
    /**
     * -- GETTER --
     * 获取 App 中用于追踪命令结果的 CompletableFuture 映射。
     * 允许 TenEnvProxy 或其他需要完成命令结果的组件访问。
     *
     * @return 存储命令 ID 到 CompletableFuture 的映射。
     */
    @Getter
    private final Map<String, CompletableFuture<CommandResult>> commandFutures; // App 自身的 CompletableFuture 映射
    private GraphConfig appConfig; // 新增：App 的整体配置，对应 property.json
    // 新增：预定义图的映射，方便通过名称查找
    private Map<String, PredefinedGraphEntry> predefinedGraphsByName;
    // private final Map<String, Connection> activeConnections; // 此行将被删除

    // 新增：提供 App 的 Runloop 访问方法
    public Runloop getRunloop() {
        return this.appRunloop;
    }

    /**
     * App 的构造函数。
     *
     * @param appUri                 应用程序的 URI。
     * @param hasOwnRunloopPerEngine 是否为每个 Engine 创建独立的 Runloop。
     * @param configFilePath         配置文件路径 (例如 property.json)，可以为 null。
     */
    public App(String appUri, boolean hasOwnRunloopPerEngine, String configFilePath) {
        this(appUri, hasOwnRunloopPerEngine, loadConfigInternal(configFilePath)); // 直接调用新的静态方法
        log.info("App {} created via config file with hasOwnRunloopPerEngine={}", appUri, hasOwnRunloopPerEngine);
    }

    /**
     * App 的主构造函数。
     *
     * @param appUri                 应用程序的 URI。
     * @param hasOwnRunloopPerEngine 是否为每个 Engine 创建独立的 Runloop。
     * @param appConfig              App 的配置对象。
     */
    public App(String appUri, boolean hasOwnRunloopPerEngine, GraphConfig appConfig) {
        this.appUri = appUri;
        this.hasOwnRunloopPerEngine = hasOwnRunloopPerEngine;
        engines = new ConcurrentHashMap<>();
        orphanConnections = Collections.synchronizedList(new java.util.ArrayList<>());
        remotes = new ConcurrentHashMap<>(); // 初始化远程连接映射
        availableExtensions = new ConcurrentHashMap<>(); // 初始化 Extension 注册表

        this.appConfig = appConfig != null ? appConfig : new GraphConfig(new ConcurrentHashMap<>()); // 使用传入的配置或默认空配置
        appRunloop = Runloop.createRunloopWithWorker("AppRunloop-%s".formatted(appUri), this);

        appCommandHandlers = new HashMap<>(); // 初始化 App 命令处理器映射
        registerAppCommandHandlers(); // 注册 App 级别的命令处理器
        inMsgs = new ManyToOneConcurrentArrayQueue<>(Runloop.DEFAULT_INTERNAL_QUEUE_CAPACITY); // 初始化 App 消息输入队列

        // 初始化 App 自身的 TenEnvProxy 实例
        appEnvProxy = new TenEnvProxy<>(appRunloop, new AppEnvImpl(this, appRunloop, appConfig), "App-" + appUri);
        commandFutures = new ConcurrentHashMap<>(); // 初始化 commandFutures

        this.pathTable = new PathTable(PathTableAttachedTo.APP, this, appEnvProxy); // <-- 将 this 替换为 appEnvProxy

        log.info("App {} created with hasOwnRunloopPerEngine={}", appUri, hasOwnRunloopPerEngine);
    }

    // 私有静态方法：加载配置文件，返回 GraphConfig
    private static GraphConfig loadConfigInternal(String configFilePath) {
        if (configFilePath != null && !configFilePath.isEmpty()) {
            try {
                String jsonContent = Files.readString(Paths.get(configFilePath));
                GraphConfig config = OBJECT_MAPPER.readValue(jsonContent, GraphConfig.class);
                log.info("App: 从 {} 加载配置成功。", configFilePath);
                return config;
            } catch (IOException e) {
                log.error("App: 加载配置文件 {} 失败: {}", configFilePath, e.getMessage());
                return new GraphConfig(new ConcurrentHashMap<>()); // 加载失败时使用空配置
            }
        } else {
            log.warn("App: 未提供配置文件路径，使用默认空配置。");
            return new GraphConfig(new ConcurrentHashMap<>()); // 创建一个空的默认配置
        }
    }

    private void registerAppCommandHandlers() {
        // 注册所有 App 级别的命令处理器
        appCommandHandlers.put(MessageType.CMD_START_GRAPH, new StartGraphCommandHandler());
        appCommandHandlers.put(MessageType.CMD_STOP_GRAPH, new StopGraphCommandHandler());
        appCommandHandlers.put(MessageType.CMD_CLOSE_APP, new CloseAppCommandHandler());
    }

    /**
     * 注册一个 Extension 类，使其可被 Engine 动态加载。
     *
     * @param extensionName  Extension 的名称。
     * @param extensionClass Extension 的 Class 对象。
     */
    public void registerExtension(String extensionName, Class<? extends Extension> extensionClass) {
        availableExtensions.put(extensionName, extensionClass);
        log.info("App: 注册 Extension: {} -> {}", extensionName, extensionClass.getName());
    }

    /**
     * 获取已注册的 Extension Class。
     *
     * @param extensionName Extension 的名称。
     * @return 对应的 Extension Class，如果不存在则返回 Optional.empty()。
     */
    public Optional<Class<? extends Extension>> getRegisteredExtension(String extensionName) {
        return Optional.ofNullable(availableExtensions.get(extensionName));
    }

    /**
     * 启动 App。
     */
    public void start() {
        log.info("App: 启动中...");
        appRunloop.start(); // 启动 App 的 Runloop

        // 从配置中加载预定义图
        predefinedGraphsByName = appConfig.getPredefinedGraphs() != null ? appConfig.getPredefinedGraphs().stream()
                .collect(Collectors.toMap(PredefinedGraphEntry::getName, entry -> entry)) : new ConcurrentHashMap<>();

        // 自动启动配置中标记为 auto_start 的预定义图
        if (appConfig.getPredefinedGraphs() != null) {
            appConfig.getPredefinedGraphs().stream()
                    .filter(PredefinedGraphEntry::isAutoStart)
                    .forEach(entry -> {
                        log.info("App: 自动启动预定义图: {}", entry.getName());
                        // 模拟发送 StartGraphCommand 来启动预定义图
                        Location srcLoc = new Location(appUri, null, null);
                        Location destLoc = new Location(appUri, entry.getGraphDefinition().getGraphId(), null);
                        StartGraphCommand startCmd = new StartGraphCommand(
                                MessageUtils.generateUniqueId(),
                                srcLoc,
                                Collections.singletonList(destLoc),
                                "Auto-start predefined graph", // message
                                entry.getGraphDefinition().getJsonContent(), // graphJsonDefinition
                                false // longRunningMode
                        );
                        // 提交命令到 App Runloop，然后由 App 的 handleInboundMessage 处理
                        appEnvProxy.sendCmd(startCmd); // 使用 appEnvProxy 提交命令
                    });
        }
        log.info("App: 已启动。");
    }

    /**
     * 停止 App。
     */
    public void stop() {
        log.info("App: 停止中...");
        // 停止所有 Engine
        engines.values().forEach(Engine::stop);
        engines.clear();

        // 关闭所有远程连接
        remotes.values().forEach(remote -> remote.shutdown()); // 修正：使用 lambda 表达式
        remotes.clear();

        // 清理孤立连接
        orphanConnections.clear();

        // 清理 App 的消息队列
        inMsgs.clear();

        // 停止 App 的 Runloop
        appRunloop.shutdown();
        // if (appTenEnv != null) {
        // appEnv.close(); // 移除此行
        // }
        log.info("App: 已停止。");
    }

    /**
     * 当有新的连接建立时被调用。
     *
     * @param connection 新的连接实例。
     */
    public void onNewConnection(Connection connection) {
        log.info("App: 接收到新连接: {}", connection.getRemoteAddress());
        // 将新连接添加到孤立连接列表，等待 StartGraphCommand 来绑定到 Engine
        orphanConnections.add(connection);
        // 使用新的 attachToApp 方法进行设置
        connection.attachToApp(this); // <-- 关键修改点
        connection.setRunloop(this.appRunloop); // 确保 Connection 关联到 App 的 Runloop
    }

    /**
     * 当连接绑定到 Engine 或不再是孤立连接时，将其从孤立连接列表中移除。
     *
     * @param connection 要移除的连接实例。
     */
    public void removeOrphanConnection(Connection connection) {
        if (connection != null && orphanConnections.remove(connection)) {
            log.info("App: 连接 {} 已从孤立连接列表中移除。", connection.getConnectionId());
        }
    }

    /**
     * 当连接关闭时被调用，移除活跃连接映射中的连接。
     *
     * @param connection 已关闭的连接实例。
     */
    public void onConnectionClosed(Connection connection) {
        if (connection != null) {
            // activeConnections.remove(connection.getConnectionId()); // 此行将被删除
            orphanConnections.remove(connection); // 如果在孤立连接中，也移除
            log.info("App: 连接 {} 已关闭并从活跃连接列表中移除。", connection.getConnectionId());
        }
    }

    /**
     * 将消息路由到指定的目标 Location。
     * 这可以是 Engine 内部的 Extension，也可以是另一个 Remote (例如另一个 App)。
     *
     * @param message          待路由的消息。
     * @param sourceConnection 消息的来源连接，如果来自内部则为 null。
     */
    public void sendMessageToLocation(Message message, Connection sourceConnection) {
        // 或者，如果从外部线程调用，则应通过 TenEnvProxy 进行代理，TenEnvProxy 会负责 postTask
        if (message.getDestLocs() == null || message.getDestLocs().isEmpty()) {
            log.warn("App: 消息 {} 没有目的地 Location，无法路由。", message.getId());
            return;
        }

        for (Location destLoc : message.getDestLocs()) {
            if (destLoc.getAppUri() != null && destLoc.getAppUri().equals(appUri)) {
                // 目标是当前 App 内部的 Engine
                if (destLoc.getGraphId() != null) {
                    Engine targetEngine = engines.get(destLoc.getGraphId());
                    if (targetEngine != null) {
                        log.debug("App: 路由消息 {} 到 Engine {}。", message.getId(), destLoc.getGraphId());
                        targetEngine.getEngineEnvProxy().sendMessage(message); // 使用 Engine 的 TenEnvProxy 提交消息
                    } else {
                        log.warn("App: 目标 Engine {} 不存在，消息 {} 无法路由。", destLoc.getGraphId(), message.getId());
                    }
                }
            } else {
                // 目标是外部 App/Remote
                String remoteId = destLoc.getAppUri(); // 使用 App URI 作为 Remote ID
                Remote targetRemote = remotes.get(remoteId);
                if (targetRemote == null) {
                    log.warn("App: 目标 Remote {} (App URI) 不存在，消息 {} 无法路由。需要实现 Remote 的创建。", remoteId,
                            message.getId());
                    // 暂时发送回源连接，表示无法路由
                    if (sourceConnection != null) {
                        appEnvProxy.sendResult(CommandResult.fail(message.getId(),
                                "Remote %s not found.".formatted(remoteId))); // 使用 appEnvProxy 发送错误结果
                    }
                } else {
                    log.debug("App: 路由消息 {} 到 Remote {}。", message.getId(), remoteId);
                    targetRemote.sendOutboundMessage(message); // 修正：使用 sendOutboundMessage
                }
            }
        }
    }

    /**
     * 路由消息到其目的地。
     *
     * @param message          待路由的消息。
     * @param sourceConnection 消息的来源连接，如果来自内部则为 null。
     */
    private void routeMessageToDestination(Message message, Connection sourceConnection) {
        List<Location> destinations = message.getDestLocs();
        if (destinations == null || destinations.isEmpty()) {
            log.warn("App: 消息 {} 没有目的地，无法路由。", message.getId());
            return;
        }

        for (Location destLoc : destinations) {
            if (appUri.equals(destLoc.getAppUri())) { // 目标是本 App 内部
                if (destLoc.getGraphId() != null) {
                    Engine targetEngine = engines.get(destLoc.getGraphId());
                    if (targetEngine != null) {
                        targetEngine.getEngineEnvProxy().sendMessage(message); // 路由到对应的 Engine 的 TenEnvProxy
                    } else {
                        log.warn("App: 目标 Engine {} 不存在，无法路由消息 {}。", destLoc.getGraphId(), message.getId());
                    }
                }
            } else { // 目标是其他 App/远程
                String remoteAppUri = destLoc.getAppUri();
                Remote remote = remotes.get(remoteAppUri);
                if (remote == null) {
                    log.warn("App: 目标远程 App URI {} 不存在 Remote 实例，消息 {} 无法路由。", remoteAppUri,
                            message.getId());
                    // 如果 remote 不存在，并且有 sourceConnection，通过 appEnvProxy 返回错误结果
                    if (sourceConnection != null) {
                        appEnvProxy.sendResult(CommandResult.fail(message.getId(),
                                "Remote %s not found.".formatted(remoteAppUri)));
                    }
                }
            }
        }
    }

    /**
     * 提交入站消息到 App 自身的队列，并异步通知 Runloop 处理。
     * 对齐 C 语言的 ten_app_push_to_in_msgs_queue。
     *
     * @param message    传入的消息。
     * @param connection 消息来源的连接，可能为 null。
     */
    @Override // App 也实现了 MessageReceiver 接口
    public boolean handleInboundMessage(Message message, Connection connection) { // 修正方法名为 handleInboundMessage
        if (message == null) {
            log.warn("App: 尝试提交空消息。");
            return false; // 返回 false 表示未成功提交
        }

        boolean success = inMsgs.offer(new QueuedMessage(message, connection));
        if (!success) {
            log.warn("App {}: 内部消息队列已满，消息 {} 被丢弃。", appUri, message.getId());
            return false;
        }

        // 异步通知 Runloop 线程处理队列中的消息
        appRunloop.wakeup();
        return true; // 返回 true 表示成功提交
    }

    @Override
    public int doWork() {
        // 从输入队列中排水并处理消息
        return inMsgs.drain(queuedMessage -> {
            Message message = queuedMessage.message;
            Connection connection = queuedMessage.connection;

            // 实际的消息处理逻辑，与原 handleInboundMessage 内部逻辑相似
            if (message instanceof Command command) {
                // C 端 App 处理入站消息时，会将其添加到 PathTable
                // 仅对非内部消息且有 ID 的命令进行 PathIn 记录
                if (command.getId() != null && !command.getId().isEmpty()) { // 检查 ID 是否存在
                    // 对于来自外部连接的命令，记录其 PathIn
                    if (connection != null) { // 仅当有实际连接时才记录 PathIn
                        pathTable.createInPath(command, connection); // <-- 记录 PathIn
                    }
                }

                AppCommandHandler handler = appCommandHandlers.get(command.getType());
                if (handler != null) {
                    try {
                        handler.handle(appEnvProxy, command, connection);
                    } catch (Exception e) {
                        log.error("App {}: 命令处理器处理命令 {} 失败: {}", appUri, command.getId(),
                                e.getMessage(),
                                e);
                        if (connection != null) {
                            CommandResult errorResult = CommandResult.fail(command.getId(),
                                    "App command handling failed: %s".formatted(e.getMessage()));
                            connection.sendOutboundMessage(errorResult);
                        }
                    }
                } else {
                    log.warn("App {}: 未知 App 级别命令类型或没有注册处理器: {}", appUri,
                            command.getType());
                    if (connection != null) {
                        CommandResult errorResult = CommandResult.fail(command.getId(),
                                "Unknown App command type or no handler registered: %s".formatted(command.getType()));
                        connection.sendOutboundMessage(errorResult);
                    }
                }
            } else if (message instanceof CommandResult commandResult) { // 新增：处理 CommandResult
                // 检查是否是需要通过 PathTable 回溯的 CommandResult
                if (commandResult.getOriginalCommandId() != null && !commandResult.getOriginalCommandId().isEmpty()) {
                    Optional<PathIn> pathInOpt = pathTable.getInPath(commandResult.getOriginalCommandId());
                    if (pathInOpt.isPresent()) {
                        PathIn pathIn = pathInOpt.get();
                        Connection originalConnection = pathIn.getSourceConnection(); // 获取原始连接

                        if (originalConnection != null) {
                            log.debug("App: 路由命令结果 {} 到原始连接 {}。", commandResult.getId(),
                                    originalConnection.getConnectionId());
                            originalConnection.sendOutboundMessage(commandResult);
                        } else {
                            log.warn("App: 原始连接为空，无法路由命令结果 {}。", commandResult.getId());
                        }
                        pathTable.removeInPath(commandResult.getOriginalCommandId()); // <-- 移除 PathIn
                    } else {
                        log.warn("App: 未找到与命令结果 {} 对应的 PathIn。可能已超时或已被处理。", commandResult.getOriginalCommandId());
                        // 如果没有 PathIn，则尝试按照 destLocs 路由
                        if (commandResult.getDestLocs() != null && !commandResult.getDestLocs().isEmpty()) {
                            routeMessageToDestination(commandResult, connection); // 对于没有 PathIn 的结果，尝试按目的地路由
                        } else if (connection != null) { // 如果有来源连接 (例如来自 Engine 的内部结果)，则直接回传给它
                            // 否则，如果没有 destLocs 且有来源 connection，则尝试直接回传
                            connection.sendOutboundMessage(commandResult);
                        }
                    }
                } else { // 对于没有 originalCommandId 的 CommandResult，或者其他非命令消息
                    if (message.getDestLocs() != null && !message.getDestLocs().isEmpty()) {
                        routeMessageToDestination(message, connection);
                    } else {
                        log.warn("App: 消息 {} (Type: {}) 没有目的地，无法处理。", message.getId(), message.getType());
                    }
                }
            } else { // 对于非命令消息，尝试路由到目标 Engine 或 Remote
                if (message.getDestLocs() != null && !message.getDestLocs().isEmpty()) {
                    routeMessageToDestination(message, connection);
                } else {
                    log.warn("App: 消息 {} (Type: {}) 没有目的地，无法处理。", message.getId(), message.getType());
                }
            }
        });
    }

    @Override
    public void onStart() {
        log.info("AppMessageDrainer for {} started.", appUri);
    }

    @Override
    public void onClose() {
        log.info("AppMessageDrainer for {} closed.", appUri);
    }

    @Override
    public String roleName() {
        return "App-%s".formatted(appUri);
    }

    /**
     * 提交一个命令到 App，并返回一个 CompletableFuture 来跟踪其结果。
     * 该方法是线程安全的，会将命令提交到 App 的 Runloop 线程进行处理。
     *
     * @param command 要提交的命令。
     * @return 一个 CompletableFuture，当命令处理完成并返回结果时，它将被完成。
     */
    public CompletableFuture<CommandResult> submitCommand(Command command) {
        CompletableFuture<CommandResult> future = new CompletableFuture<>();
        // 确保在 App 的 Runloop 线程中执行
        if (appRunloop.isNotCurrentThread()) {
            appRunloop.postTask(() -> {
                // 将 CompletableFuture 放入 commandFutures 映射
                commandFutures.put(command.getId(), future);
                // 提交命令到消息队列
                handleInboundMessage(command, null); // 使用已有的 handleInboundMessage
            });
        } else {
            // 如果已经在 Runloop 线程，则直接执行
            commandFutures.put(command.getId(), future);
            handleInboundMessage(command, null); // 使用已有的 handleInboundMessage
        }
        return future;
    }

    // 内部类，用于包装消息和其来源连接
    private record QueuedMessage(Message message, Connection connection) {
    }

}