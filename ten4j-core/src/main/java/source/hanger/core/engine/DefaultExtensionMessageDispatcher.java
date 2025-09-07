package source.hanger.core.engine;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.ExtensionInfo;
import source.hanger.core.graph.DestinationInfo;
import source.hanger.core.graph.RoutingRuleDefinition;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message;
import source.hanger.core.message.command.Command;
import source.hanger.core.path.PathTable;
import org.apache.commons.lang3.StringUtils;

import static java.util.Collections.*;
import static org.apache.commons.collections4.CollectionUtils.*;

/**
 * `DefaultExtensionMessageDispatcher` 是 `ExtensionMessageDispatcher` 接口的默认实现。
 * 它负责将消息从 `Engine` 派发到相应的 `Extension` 实例。
 * 消息的路由决策委托给 `PathTable`。
 */
@Slf4j
public class DefaultExtensionMessageDispatcher implements ExtensionMessageDispatcher {

    private final EngineExtensionContext engineExtensionContext;
    private final ConcurrentMap<String, CompletableFuture<Object>> commandFutures;

    public DefaultExtensionMessageDispatcher(EngineExtensionContext engineExtensionContext,
        ConcurrentMap<String, CompletableFuture<Object>> commandFutures) {
        this.engineExtensionContext = engineExtensionContext;
        this.commandFutures = commandFutures;
        log.info("DefaultExtensionMessageDispatcher created for Engine: {}",
            engineExtensionContext.getEngine().getGraphId());
    }

    @Override
    public void dispatchMessage(Message message) {
        String engineId = engineExtensionContext.getEngine().getGraphId();
        log.debug("DefaultExtensionMessageDispatcher: 正在派发消息: ID={}, Name={}, Type={}, SrcLoc={}, DestLocs={}",
            message.getId(), message.getName(), message.getType(), message.getSrcLoc(), message.getDestLocs());

        List<Location> targetLocations = message.getDestLocs();

        if (isEmpty(targetLocations)) {
            log.warn(
                "DefaultExtensionMessageDispatcher: 消息 {} (Name: {}, Type: {}) 没有明确的 Extension "
                    + "目的地，也无法从图配置中确定，无法派发。",
                message.getId(), message.getName(), message.getType());
            return;
        }

        for (Location targetLocation : targetLocations) {
            // 确保目的地是当前 Engine 内部的 Extension
            if (!targetLocation.getGraphId().equals(
                engineExtensionContext.getEngine().getGraphDefinition().getGraphId())) {
                log.warn(
                    "DefaultExtensionMessageDispatcher: 消息 {} (Name: {}) 的目的地 {} 不属于当前 Engine {}，跳过内部派发。",
                    message.getId(), message.getName(), targetLocation, engineId);
                continue;
            }

            // 消息派发给 ExtensionContext
            Message finalMessageToSend = message; // 默认使用原始消息
            // 只有当原始消息有多个目的地，并且需要为每个目的地克隆时，才执行克隆
            // 现在 targetLocations 已经是经过处理的最终目的地列表，如果列表中有多个目的地
            // 并且消息是可变的，我们才需要为每个目的地创建副本
            if (size(targetLocations) > 1 && !(message instanceof CommandResult)) { // CommandResult 不应被克隆以避免 PathTable
                // 混乱
                finalMessageToSend = message.cloneBuilder().build();
                // 为克隆的消息设置单目的地，避免在下一层再次处理多目的地
                finalMessageToSend.setDestLocs(singletonList(targetLocation));
            }

            try {
                // 只有当 finalMessageToSend 的目的地列表为单个目标时，才传递其 extensionName
                // 否则，保持原始行为，由 engineExtensionContext 内部处理
                // 这里已经将 targetLocations 明确为单目的地或通过 clone 确保了这一点
                engineExtensionContext.dispatchMessageToExtension(finalMessageToSend,
                    targetLocation.getExtensionName());
            } catch (Exception e) {
                log.error("DefaultExtensionMessageDispatcher: 派发消息 {} (Name: {}) 到 Extension {} 失败: {}",
                    message.getId(), message.getName(), targetLocation.getExtensionName(), e.getMessage(), e);
                // 对于命令，如果派发失败，应该使对应的 CompletableFuture 失败
                // 这部分逻辑现在也主要由 Engine.processMessage 负责，这里作为兜底。
                if (message instanceof Command command) { // 修正：改回 Command 类型
                    String commandId = command.getId(); // 修正：使用 command.getId()
                    if (commandFutures.containsKey(commandId)) {
                        commandFutures.get(commandId).completeExceptionally(
                            new RuntimeException(
                                "Failed to dispatch command to extension: %s".formatted(e.getMessage())));
                        commandFutures.remove(commandId);
                    }
                }
            }
        }
    }
}