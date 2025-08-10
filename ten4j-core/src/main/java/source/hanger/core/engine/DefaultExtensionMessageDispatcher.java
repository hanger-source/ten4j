package source.hanger.core.engine;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import source.hanger.core.message.Location;
import source.hanger.core.message.Message;
import source.hanger.core.message.command.Command;
import source.hanger.core.path.PathTable;
import lombok.extern.slf4j.Slf4j;

/**
 * `DefaultExtensionMessageDispatcher` 是 `ExtensionMessageDispatcher` 接口的默认实现。
 * 它负责将消息从 `Engine` 派发到相应的 `Extension` 实例。
 * 消息的路由决策委托给 `PathTable`。
 */
@Slf4j
public class DefaultExtensionMessageDispatcher implements ExtensionMessageDispatcher {

    private final EngineExtensionContext engineExtensionContext;
    private final PathTable pathTable;
    private final ConcurrentMap<Long, CompletableFuture<Object>> commandFutures;

    public DefaultExtensionMessageDispatcher(EngineExtensionContext engineExtensionContext,
            ConcurrentMap<Long, CompletableFuture<Object>> commandFutures) {
        this.engineExtensionContext = engineExtensionContext;
        pathTable = engineExtensionContext.getPathTable(); // 从 ExtensionContext 获取 PathTable
        this.commandFutures = commandFutures;
        log.info("DefaultExtensionMessageDispatcher created for Engine: {}",
            engineExtensionContext.getEngine().getGraphId());
    }

    @Override
    public void dispatchMessage(Message message) {
        String engineId = engineExtensionContext.getEngine().getGraphId();
        log.debug("DefaultExtensionMessageDispatcher: 正在派发消息: ID={}, Type={}, SrcLoc={}, DestLocs={}",
                message.getId(), message.getType(), message.getSrcLoc(), message.getDestLocs());

        // 直接使用消息的目的地，PathTable不再负责路由解析
        List<Location> targetLocations = message.getDestLocs();

        if (targetLocations == null || targetLocations.isEmpty()) {
            log.warn("DefaultExtensionMessageDispatcher: 消息 {} 没有明确的 Extension 目的地，无法派发。", message.getId());
            // 对于命令，如果没有目的地，可能需要返回错误结果。
            // 但这部分逻辑现在应该在 Engine.processMessage 中处理，这里只作为最后的警告。
            return;
        }

        for (Location targetLocation : targetLocations) {
            // 确保目的地是当前 Engine 内部的 Extension
            if (!targetLocation.getGraphId().equals(
                engineExtensionContext.getEngine().getGraphDefinition().getGraphId())) {
                log.warn("DefaultExtensionMessageDispatcher: 消息 {} 的目的地 {} 不属于当前 Engine {}，跳过内部派发。",
                        message.getId(), targetLocation, engineId);
                continue;
            }

            // 消息派发给 ExtensionContext
            Message finalMessageToSend = message; // 默认使用原始消息
            if (targetLocations.size() > 1) {
                // 如果消息需要发送到多个目的地，并且消息是可变的，则需要克隆。
                // 目前 Message 已被设计为可变，所以这里可以简单地克隆以避免副作用。
                // 更安全的做法是设计为不可变消息或明确每次都克隆。
                try {
                    finalMessageToSend = message.clone();
                    finalMessageToSend.setDestLocs(Collections.singletonList(targetLocation)); // 设置单目的地
                } catch (CloneNotSupportedException e) {
                    log.error("DefaultExtensionMessageDispatcher: 克隆消息 {} 失败，无法发送到多个目的地: {}", message.getId(),
                            e.getMessage());
                    continue;
                }
            }

            try {
                engineExtensionContext.dispatchMessageToExtension(finalMessageToSend,
                    targetLocation.getExtensionName());
            } catch (Exception e) {
                log.error("DefaultExtensionMessageDispatcher: 派发消息 {} 到 Extension {} 失败: {}",
                    message.getId(), targetLocation.getExtensionName(), e.getMessage(), e);
                // 对于命令，如果派发失败，应该使对应的 CompletableFuture 失败
                // 这部分逻辑现在也主要由 Engine.processMessage 负责，这里作为兜底。
                if (message instanceof Command command) {
                    long commandId = Long.parseLong(command.getId());
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

    @Override
    public void dispatchOtherMessage(Message message) {
        dispatchMessage(message); // 委托给 dispatchMessage
    }
}