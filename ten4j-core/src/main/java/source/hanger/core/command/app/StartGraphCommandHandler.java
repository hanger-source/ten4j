package source.hanger.core.command.app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.app.App;
import source.hanger.core.app.AppEnvImpl;
import source.hanger.core.connection.Connection;
import source.hanger.core.engine.Engine;
import source.hanger.core.extension.ExtensionGroupInfo;
import source.hanger.core.extension.ExtensionInfo;
import source.hanger.core.graph.GraphDefinition;
import source.hanger.core.graph.GraphLoader;
import source.hanger.core.graph.PredefinedGraphEntry;
import source.hanger.core.graph.runtime.PredefinedGraphRuntimeInfo;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.StartGraphCommand;
import source.hanger.core.remote.Remote;
import source.hanger.core.tenenv.TenEnvProxy;

/**
 * `StartGraphCommandHandler` 处理 `StartGraphCommand` 命令，负责启动 Engine。
 */
@Slf4j
public class StartGraphCommandHandler implements AppCommandHandler {

    @Override
    public Object handle(TenEnvProxy<AppEnvImpl> appEnvProxy, Command command, Connection connection) { // 修改签名
        App app = appEnvProxy.targetEnv().getApp(); // 通过代理获取 App 实例

        if (!(command instanceof StartGraphCommand startCommand)) {
            log.warn("StartGraphCommandHandler 收到非 StartGraphCommand 命令: {}", command.getType());
            // 返回失败结果
            if (connection != null) {
                CommandResult errorResult = CommandResult.fail(command.getId(),
                    command.getType(), command.getName(), "Unexpected command type for StartGraphHandler.");
                connection.sendOutboundMessage(errorResult);
            }
            return null;
        }

        String targetGraphId = null;

        if (startCommand.getPredefinedGraphName() != null && !startCommand.getPredefinedGraphName().isEmpty()) {
            targetGraphId = startCommand.getPredefinedGraphName();
        } else if (startCommand.getDestLocs() != null && !startCommand.getDestLocs().isEmpty()) {
            targetGraphId = startCommand.getDestLocs().getFirst().getGraphId();
        } else if (startCommand.getGraphJsonDefinition() != null) {
            // 如果提供了 JSON 定义，解析它来获取 graphId
            try {
                GraphDefinition tempGraphDef = GraphLoader
                    .loadGraphDefinitionFromJson(startCommand.getGraphJsonDefinition(),
                        startCommand.getProperties());
                targetGraphId = tempGraphDef.getGraphId();
            } catch (JsonProcessingException e) {
                log.error("StartGraphCommandHandler: 解析 graphJsonDefinition 失败: {}", e.getMessage());
                // 这里不返回，继续尝试从预定义图加载
            }
        }

        // 优先从预定义图中查找 GraphDefinition
        GraphDefinition graphDefinition = null;
        if (targetGraphId != null && app.getPredefinedGraphsByName().containsKey(targetGraphId)) {
            PredefinedGraphEntry entry = app.getPredefinedGraphsByName().get(targetGraphId);
            if (entry != null) {
                // 从 PredefinedGraphEntry 获取 GraphDefinition，然后序列化
                GraphDefinition loadedDefinition = entry.getGraph(); // 直接获取 GraphDefinition
                try {
                    // 注意：这里需要一个 ObjectMapper 来序列化 loadedDefinition
                    // StartGraphCommandHandler 缺乏 ObjectMapper 实例，可以考虑作为依赖注入或静态实例
                    // 暂时使用新的 ObjectMapper 实例
                    String graphJson = new ObjectMapper().writeValueAsString(loadedDefinition);
                    graphDefinition = GraphLoader.loadGraphDefinitionFromJson(graphJson,
                        startCommand.getProperties()); // 重新加载以确保完整性
                    graphDefinition.setGraphId(UUID.randomUUID().toString());
                    log.info("StartGraphCommandHandler: 找到预定义图 {}。", targetGraphId);
                    graphDefinition.setGraphName(targetGraphId);
                } catch (JsonProcessingException e) {
                    log.error("StartGraphCommandHandler: 自动启动图 {} 时，序列化/解析预定义图的 JSON 失败: {}",
                        entry.getName(),
                        e.getMessage());
                    // 即使解析失败，也不应阻止后续尝试从 command 的 json 定义加载
                }
            }
        }

        // 如果没有找到预定义图，则尝试从命令中获取 JSON 定义
        if (graphDefinition == null && startCommand.getGraphJsonDefinition() != null) {
            try {
                graphDefinition = GraphLoader.loadGraphDefinitionFromJson(startCommand.getGraphJsonDefinition(),
                    startCommand.getProperties());
                graphDefinition.setGraphId(graphDefinition.getGraphId());
                log.info("StartGraphCommandHandler: 从 JSON 定义创建图 {}。", graphDefinition.getGraphId());
            } catch (JsonProcessingException e) {
                log.error("StartGraphCommandHandler: 创建 GraphDefinition 失败: {}", e.getMessage(), e);
                if (connection != null) {
                    CommandResult errorResult = CommandResult.fail(command.getId(),
                        command.getType(), command.getName(),
                        "Failed to create GraphDefinition: %s".formatted(e.getMessage()));
                    connection.sendOutboundMessage(errorResult);
                }
                return null;
            }
        }

        if (graphDefinition == null) {
            log.warn("StartGraphCommandHandler: 无法获取 Graph 定义，命令 {} 无法处理。", command.getId());
            if (connection != null) {
                CommandResult errorResult = CommandResult.fail(command, "Graph definition not found.");
                connection.sendOutboundMessage(errorResult);
            }
            return null;
        }

        String graphId = graphDefinition.getGraphId();
        Engine engine;
        boolean engineNewlyStarted = false; // 标记 Engine 是否是新启动的

        if (app.getEngines().containsKey(graphId)) { // 使用 app 实例
            engine = app.getEngines().get(graphId);
            log.warn("StartGraphCommandHandler: Engine {} 已经存在，不再重复启动，但将附加连接。", graphId);
        } else {
            // 创建并启动新的 Engine
            engine = new Engine(graphId, graphDefinition, app, app.isHasOwnRunloopPerEngine()); // 使用 app 实例
            app.getEngines().put(graphId, engine); // 使用 app 实例
            engine.start(); // 启动 Engine
            engineNewlyStarted = true;
            log.info("StartGraphCommandHandler: Engine {} 启动成功。", graphId);
        }

        // 如果命令来源于一个孤立连接，那么该连接现在已绑定到 Engine，从孤立列表中移除它
        if (connection != null) {
            app.removeOrphanConnection(connection); // 从 App 的孤立连接列表中移除

            // 关键：将 Connection 依附到 Engine
            connection.attachToEngine(engine);

            // 获取或创建 Remote 实例，并将 Connection 依附到 Remote
            Remote remote = engine.getOrCreateRemote(connection.getUri(), graphId, connection);
            if (remote == null) {
                throw new IllegalStateException("无法创建或获取 Remote 实例");
            }

            // 获取 Engine 中加载的 ExtensionInfo 和 ExtensionGroupInfo
            List<ExtensionInfo> runtimeExtensionInfos = engine.getEngineExtensionContext().getAllExtensionInfos();
            List<ExtensionGroupInfo> runtimeExtensionGroupInfos = engine.getEngineExtensionContext()
                .getAllExtensionGroupInfos();

            // 构建 PredefinedGraphRuntimeInfo 实例
            // 注意：PredefinedGraphEntry 中没有 singleton 字段，这里暂时默认为 false。
            // 如果 singleton 应该从其他地方（例如 GraphDefinition）获取，需要进一步明确。
            PredefinedGraphRuntimeInfo runtimeInfo = new PredefinedGraphRuntimeInfo(
                graphDefinition.getGraphName(), // 使用 graph name
                false, // auto_start 暂时默认为 false，因为这里是运行时信息，而非配置
                false, // singleton 暂时默认为 false
                runtimeExtensionInfos,
                runtimeExtensionGroupInfos);

            // 将 PredefinedGraphRuntimeInfo 序列化为 JSON 字符串，添加到 CommandResult 的 properties 中
            Map<String, Object> properties = new HashMap<>();
            properties.put("graph_id", graphId);
            properties.put("app_uri", app.getAppUri());
            // properties.put("predefined_graph_runtime_info", new
            // ObjectMapper().writeValueAsString(runtimeInfo));

            CommandResult successResult;
            if (engineNewlyStarted) {
                successResult = CommandResult.success(
                    command.getId(),
                    command.getType(),
                    command.getName(),
                    "Engine %s started successfully.".formatted(graphId),
                    properties // 传递 properties
                );
            } else {
                successResult = CommandResult.success(
                    command.getId(),
                    command.getType(),
                    command.getName(),
                    "Engine %s already running, connection attached.".formatted(graphId),
                    properties // 传递 properties
                );
            }
            connection.sendOutboundMessage(successResult);
        }
        return null;
    }
}