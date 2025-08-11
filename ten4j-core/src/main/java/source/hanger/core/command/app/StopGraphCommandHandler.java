package source.hanger.core.command.app;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.app.App;
import source.hanger.core.app.AppEnvImpl;
import source.hanger.core.connection.Connection;
import source.hanger.core.engine.Engine;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnvProxy;

/**
 * `StopGraphCommandHandler` 处理 `StopGraphCommand` 命令，负责停止 Engine。
 */
@Slf4j
public class StopGraphCommandHandler implements AppCommandHandler {

    @Override
    public Object handle(TenEnvProxy<AppEnvImpl> appEnvProxy, Command command, Connection connection) { // 修改签名
        App app = appEnvProxy.targetEnv().getApp(); // 通过代理获取 App 实例

        // StopGraphCommand 可能没有特定的 Command 子类，这里直接从 Command 中获取 graphId
        String graphIdToStop = command.getDestLocs() != null && !command.getDestLocs().isEmpty()
                ? command.getDestLocs().getFirst().getGraphId()
                : null;

        if (graphIdToStop != null && app.getEngines().containsKey(graphIdToStop)) { // 使用 app 实例
            Engine engineToStop = app.getEngines().remove(graphIdToStop); // 从 App 中移除 Engine
            if (engineToStop != null) {
                engineToStop.stop(); // 停止 Engine
                log.info("StopGraphCommandHandler: Engine {} 已停止并从 App 中移除。", graphIdToStop);
                if (connection != null) {
                    CommandResult successResult = CommandResult.success(command,
                            "Engine %s stopped successfully.".formatted(graphIdToStop));
                    connection.sendOutboundMessage(successResult);
                }
            } else {
                log.warn("StopGraphCommandHandler: 尝试停止不存在的 Engine {}。", graphIdToStop);
                if (connection != null) {
                    CommandResult errorResult = CommandResult.fail(command,
                            "Engine %s not found or already stopped.".formatted(graphIdToStop));
                    connection.sendOutboundMessage(errorResult);
                }
            }
        } else {
            log.warn("StopGraphCommandHandler: 无法停止 Engine，因为 Graph ID 无效或 Engine 不存在: {}", graphIdToStop);
            if (connection != null) {
                CommandResult errorResult = CommandResult.fail(command,
                        "Invalid Graph ID or Engine not found: %s".formatted(graphIdToStop));
                connection.sendOutboundMessage(errorResult);
            }
        }
        return null;
    }
}