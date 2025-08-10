package com.tenframework.core.command.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.app.App;
import com.tenframework.core.connection.Connection;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.graph.GraphDefinition;
import com.tenframework.core.graph.PredefinedGraphEntry;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.StartGraphCommand;
import com.tenframework.core.tenenv.TenEnvProxy;
import lombok.extern.slf4j.Slf4j;
import com.tenframework.core.app.AppEnvImpl;

/**
 * `StartGraphCommandHandler` 处理 `StartGraphCommand` 命令，负责启动 Engine。
 */
@Slf4j
public class StartGraphCommandHandler implements AppCommandHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Object handle(TenEnvProxy<AppEnvImpl> appEnvProxy, Command command, Connection connection) { // 修改签名
        App app = appEnvProxy.targetEnv().getApp(); // 通过代理获取 App 实例

        if (!(command instanceof StartGraphCommand)) {
            log.warn("StartGraphCommandHandler 收到非 StartGraphCommand 命令: {}", command.getType());
            // 返回失败结果
            if (connection != null) {
                CommandResult errorResult = CommandResult.fail(command.getId(),
                        "Unexpected command type for StartGraphHandler.");
                connection.sendOutboundMessage(errorResult);
            }
            return null;
        }

        StartGraphCommand startCommand = (StartGraphCommand) command;
        String targetGraphId = null;

        if (startCommand.getPredefinedGraphName() != null && !startCommand.getPredefinedGraphName().isEmpty()) {
            targetGraphId = startCommand.getPredefinedGraphName();
        } else if (startCommand.getDestLocs() != null && !startCommand.getDestLocs().isEmpty()) {
            targetGraphId = startCommand.getDestLocs().get(0).getGraphId();
        } else if (startCommand.getGraphJsonDefinition() != null) {
            // 如果提供了 JSON 定义，解析它来获取 graphId
            try {
                GraphDefinition tempGraphDef = new GraphDefinition(app.getAppUri(),
                        startCommand.getGraphJsonDefinition());
                targetGraphId = tempGraphDef.getGraphId();
            } catch (Exception e) {
                log.error("StartGraphCommandHandler: 解析 graphJsonDefinition 失败: {}", e.getMessage());
            }
        }

        // 优先从预定义图中查找 GraphDefinition
        GraphDefinition graphDefinition = null;
        if (targetGraphId != null && app.getPredefinedGraphsByName().containsKey(targetGraphId)) {
            PredefinedGraphEntry entry = app.getPredefinedGraphsByName().get(targetGraphId); // 使用 app 实例
            if (entry != null) {
                graphDefinition = entry.getGraphDefinition();
                log.info("StartGraphCommandHandler: 找到预定义图 {}。", targetGraphId);
            }
        }

        // 如果没有找到预定义图，则尝试从命令中获取 JSON 定义
        if (graphDefinition == null && startCommand.getGraphJsonDefinition() != null) {
            try {
                graphDefinition = new GraphDefinition(app.getAppUri(), startCommand.getGraphJsonDefinition()); // 使用 app
                // 实例
                log.info("StartGraphCommandHandler: 从 JSON 定义创建图 {}。", graphDefinition.getGraphId());
            } catch (Exception e) {
                log.error("StartGraphCommandHandler: 创建 GraphDefinition 失败: {}", e.getMessage(), e);
                if (connection != null) {
                    CommandResult errorResult = CommandResult.fail(command.getId(),
                            "Failed to create GraphDefinition: " + e.getMessage());
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
                CommandResult errorResult = CommandResult.fail(command.getId(), "Engine already exists: " + graphId);
                connection.sendOutboundMessage(errorResult);
            }
            return null;
        }

        // 创建并启动新的 Engine
        Engine engine = new Engine(graphId, graphDefinition, app, app.isHasOwnRunloopPerEngine()); // 使用 app 实例
        app.getEngines().put(graphId, engine); // 使用 app 实例

        engine.start(); // 启动 Engine

        log.info("StartGraphCommandHandler: Engine {} 启动成功。", graphId);
        if (connection != null) {
            CommandResult successResult = CommandResult.success(command.getId(),
                    "Engine " + graphId + " started successfully.");
            connection.sendOutboundMessage(successResult);
        }
        return null;
    }
}