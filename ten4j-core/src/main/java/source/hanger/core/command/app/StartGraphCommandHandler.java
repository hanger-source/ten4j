package source.hanger.core.command.app;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.app.App;
import source.hanger.core.app.AppEnvImpl;
import source.hanger.core.connection.Connection;
import source.hanger.core.engine.Engine;
import source.hanger.core.graph.GraphDefinition;
import source.hanger.core.graph.PredefinedGraphEntry;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.StartGraphCommand;
import source.hanger.core.remote.Remote;
import source.hanger.core.tenenv.TenEnvProxy;
import source.hanger.core.graph.GraphLoader;

import com.fasterxml.jackson.core.JsonProcessingException;

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
                        "Unexpected command type for StartGraphHandler.");
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
                        .loadGraphDefinitionFromJson(startCommand.getGraphJsonDefinition());
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
                // 从 PredefinedGraphEntry 获取原始 JSON 字符串，然后解析为 GraphDefinition
                try {
                    graphDefinition = GraphLoader.loadGraphDefinitionFromJson(entry.getGraphJsonContent());
                    log.info("StartGraphCommandHandler: 找到预定义图 {}。", targetGraphId);
                } catch (JsonProcessingException e) {
                    log.error("StartGraphCommandHandler: 自动启动图 {} 时，解析预定义图的 JSON 失败: {}", entry.getName(),
                            e.getMessage());
                    // 即使解析失败，也不应阻止后续尝试从 command 的 json 定义加载
                }
            }
        }

        // 如果没有找到预定义图，则尝试从命令中获取 JSON 定义
        if (graphDefinition == null && startCommand.getGraphJsonDefinition() != null) {
            try {
                graphDefinition = GraphLoader.loadGraphDefinitionFromJson(startCommand.getGraphJsonDefinition());
                log.info("StartGraphCommandHandler: 从 JSON 定义创建图 {}。", graphDefinition.getGraphId());
            } catch (JsonProcessingException e) {
                log.error("StartGraphCommandHandler: 创建 GraphDefinition 失败: {}", e.getMessage(), e);
                if (connection != null) {
                    CommandResult errorResult = CommandResult.fail(command.getId(),
                            "Failed to create GraphDefinition: %s".formatted(e.getMessage()));
                    connection.sendOutboundMessage(errorResult);
                }
                return null;
            }
        }

        if (graphDefinition == null) {
            log.warn("StartGraphCommandHandler: 无法获取 Graph 定义，命令 {} 无法处理。", command.getId());
            if (connection != null) {
                CommandResult errorResult = CommandResult.fail(command.getId(), "Graph definition not found.");
                connection.sendOutboundMessage(errorResult);
            }
            return null;
        }

        String graphId = graphDefinition.getGraphId();
        if (app.getEngines().containsKey(graphId)) { // 使用 app 实例
            log.warn("StartGraphCommandHandler: Engine {} 已经存在，不再重复启动。", graphId);
            if (connection != null) {
                CommandResult errorResult = CommandResult.fail(command.getId(),
                        "Engine already exists: %s".formatted(graphId));
                connection.sendOutboundMessage(errorResult);
            }
            return null;
        }

        // 创建并启动新的 Engine
        Engine engine = new Engine(graphId, graphDefinition, app, app.isHasOwnRunloopPerEngine()); // 使用 app 实例
        app.getEngines().put(graphId, engine); // 使用 app 实例

        engine.start(); // 启动 Engine

        // 如果命令来源于一个孤立连接，那么该连接现在已绑定到 Engine，从孤立列表中移除它
        if (connection != null) {
            app.removeOrphanConnection(connection); // 从 App 的孤立连接列表中移除

            // 关键：在创建 Remote 之前，先将 Connection 依附到 Engine
            connection.attachToEngine(engine);

            // 获取或创建 Remote 实例，并将 Connection 依附到 Remote
            // 修正：使用 connection.getUri() 作为 Remote 的唯一标识，而不是 app.getAppUri()
            Remote remote = engine.getOrCreateRemote(connection.getUri(), graphId, connection);
            if (remote == null) {
                throw new IllegalStateException("无法创建或获取 Remote 实例");
            }
        }

        log.info("StartGraphCommandHandler: Engine {} 启动成功。", graphId);
        if (connection != null) {
            CommandResult successResult = CommandResult.success(command.getId(),
                    "Engine %s started successfully.".formatted(graphId));
            connection.sendOutboundMessage(successResult);
        }
        return null;
    }
}