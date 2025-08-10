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
import source.hanger.core.remote.Remote;
import source.hanger.core.runloop.Runloop;
import source.hanger.core.tenenv.TenEnvProxy;
import source.hanger.core.util.MessageUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

/**
 * App 类作为 Ten 框架的顶层容器和协调器。
 * 它管理 Engine 实例，处理传入的连接，并路由消息。
 * 对应 C 语言中的 ten_app_t 结构体。
 */
@Slf4j
@Getter
public class App implements Agent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Map<String, Engine> engines; // 管理所有活跃的 Engine 实例，key 为 graphId
    private final List<Connection> orphanConnections; // 尚未绑定到 Engine 的连接
    // private final PathTable pathTable; // App 级别 PathTable - 移除 App 级别 PathTable
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

    // protected AppTenEnv appTenEnv; // 移除此字段
    // private final TenComponentRuntime tenComponentRuntime; // 移除此字段

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

        log.info("App {} created with hasOwnRunloopPerEngine={}", appUri, hasOwnRunloopPerEngine);
    }

    private void registerAppCommandHandlers() {
        // 注册所有 App 级别的命令处理器
        appCommandHandlers.put(MessageType.CMD_START_GRAPH, new StartGraphCommandHandler());
        appCommandHandlers.put(MessageType.CMD_STOP_GRAPH, new StopGraphCommandHandler());
        appCommandHandlers.put(MessageType.CMD_CLOSE_APP, new CloseAppCommandHandler());
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
        remotes.values().forEach(Remote::shutdown);
        remotes.clear();

        // 清理孤立连接
        orphanConnections.clear();

        // 清理 App 的消息队列
        inMsgs.clear();

        // 停止 App 的 Runloop
        appRunloop.shutdown();
        // if (appTenEnv != null) {
        // appTenEnv.close(); // 移除此行
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
        // connection.setMessageReceiver(this); // 移除此行，AppTenEnv 现在是 MessageReceiver
    }

    /**
     * 将消息路由到指定的目标 Location。
     * 这可以是 Engine 内部的 Extension，也可以是另一个 Remote (例如另一个 App)。
     *
     * @param message          待路由的消息。
     * @param sourceConnection 消息的来源连接，如果来自内部则为 null。
     */
    public void sendMessageToLocation(Message message, Connection sourceConnection) {
        // 此处不需要 postTask，因为此方法预期在 App 的 Runloop 线程上调用
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
                                "Remote " + remoteId + " not found.")); // 使用 appEnvProxy 发送错误结果
                    }
                } else {
                    log.debug("App: 路由消息 {} 到 Remote {}。", message.getId(), remoteId);
                    targetRemote.sendMessage(message);
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
                    log.warn("App: 目标远程 App URI {} 不存在 Remote 实例，消息 {} 无法路由。", remoteAppUri, message.getId());
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
    public void submitInboundMessage(Message message, Connection connection) {
        if (message == null) {
            log.warn("App: 尝试提交空消息。");
            return;
        }

        boolean success = inMsgs.offer(new QueuedMessage(message, connection));
        if (!success) {
            log.warn("App {}: 内部消息队列已满，消息 {} 被丢弃。", appUri, message.getId());
            return;
        }

        // 异步通知 Runloop 线程处理队列中的消息
        appRunloop.wakeup();
    }

    @Override
    public int doWork() {
        // 从输入队列中排水并处理消息
        return inMsgs.drain(queuedMessage -> {
            Message message = queuedMessage.message;
            Connection connection = queuedMessage.connection;
            // 实际的消息处理逻辑，与原 handleInboundMessage 内部逻辑相似
            if (message instanceof Command command) {
                AppCommandHandler handler = appCommandHandlers.get(command.getType());
                if (handler != null) {
                    try {
                        // 将 App.this 替换为 appEnvProxy，因为 AppCommandHandler 期望 TenEnvProxy
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
     * 处理传入的命令结果消息。
     * 这是 App Runloop 线程中的核心处理逻辑。
     *
     * @param commandResult 传入的命令结果。
     */
    public void routeCommandResult(CommandResult commandResult) {
        // 确保在 App 的 Runloop 线程中执行
        if (appRunloop.isNotCurrentThread()) {
            appRunloop.postTask(() -> routeCommandResult(commandResult));
            return;
        }

        String originalCommandId = commandResult.getOriginalCommandId();
        CompletableFuture<CommandResult> future = commandFutures.remove(originalCommandId);
        if (future != null) {
            if (commandResult.getStatusCode() == 0) {
                future.complete(commandResult);
            } else {
                future.completeExceptionally(new RuntimeException(
                        "Command failed with status: %d, Detail: %s".formatted(commandResult.getStatusCode(),
                                commandResult.getDetail())));
            }
        } else {
            log.warn("App {}: 未找到与命令结果 {} 对应的 Future。", appUri, commandResult.getOriginalCommandId());
        }

        // 如果命令结果有返回地址，可能需要向上路由或发送给 Remote
        if (commandResult.getDestLocs() != null && !commandResult.getDestLocs().isEmpty()) {
            routeMessageToDestination(commandResult, null); // 使用已有的路由方法
        }
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
                submitInboundMessage(command, null); // 使用已有的 submitInboundMessage
            });
        } else {
            // 如果已经在 Runloop 线程，则直接执行
            commandFutures.put(command.getId(), future);
            submitInboundMessage(command, null); // 使用已有的 submitInboundMessage
        }
        return future;
    }

    // 内部类，用于包装消息和其来源连接
    private record QueuedMessage(Message message, Connection connection) {
    }

}