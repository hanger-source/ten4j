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
    private final PathTable pathTable;
    private final ConcurrentMap<String, CompletableFuture<Object>> commandFutures;

    public DefaultExtensionMessageDispatcher(EngineExtensionContext engineExtensionContext,
        ConcurrentMap<String, CompletableFuture<Object>> commandFutures) {
        this.engineExtensionContext = engineExtensionContext;
        pathTable = engineExtensionContext.getPathTable(); // 从 ExtensionContext 获取 PathTable
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

        // 如果消息没有明确的目的地，则尝试从图配置中确定
        if (isEmpty(targetLocations)) {
            targetLocations = determineMessageDestinationsFromGraph(message);
            if (isEmpty(targetLocations)) {
                log.warn(
                    "DefaultExtensionMessageDispatcher: 消息 {} (Name: {}, Type: {}) 没有明确的 Extension "
                        + "目的地，也无法从图配置中确定，无法派发。",
                    message.getId(), message.getName(), message.getType());
                return;
            }
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
                try {
                    finalMessageToSend = message.clone();
                    // 为克隆的消息设置单目的地，避免在下一层再次处理多目的地
                    finalMessageToSend.setDestLocs(singletonList(targetLocation));
                } catch (CloneNotSupportedException e) {
                    log.error("DefaultExtensionMessageDispatcher: 克隆消息 {} (Name: {}) 失败，无法发送到多个目的地: {}",
                        message.getId(), message.getName(), e.getMessage());
                    continue;
                }
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

    @Override
    public void dispatchOtherMessage(Message message) {
        dispatchMessage(message); // 委托给 dispatchMessage
    }

    /**
     * 根据图的配置（Graph Definition）为消息确定目的地。
     * 模拟 C 语言的 `_extension_determine_out_msg_dest_from_graph` 逻辑。
     * 此方法在消息没有明确目的地时被调用。
     *
     * @param message 待处理的消息。
     * @return 包含根据图配置确定的目的地 Location 列表。如果未找到规则，则返回空列表。
     */
    private List<Location> determineMessageDestinationsFromGraph(Message message) {
        // 1. 获取消息的源 Extension 名称
        String sourceExtensionName = message.getSrcLoc() != null ? message.getSrcLoc().getExtensionName() : null;
        if (StringUtils.isEmpty(sourceExtensionName)) {
            log.warn(
                "DefaultExtensionMessageDispatcher: 消息 {} (Name: {}, Type: {}) 没有源 Extension，无法从图配置中确定目的地。",
                message.getId(), message.getName(), message.getType());
            return emptyList();
        }

        // 2. 获取源 Extension 的 ExtensionInfo (其中包含 AllMessageDestInfo)
        ExtensionInfo sourceExtInfo = engineExtensionContext.getExtensionInfo(sourceExtensionName);
        if (sourceExtInfo == null || sourceExtInfo.getMsgDestInfo() == null) {
            log.warn(
                "DefaultExtensionMessageDispatcher: 未找到源 Extension {} 的消息目的地信息 (AllMessageDestInfo) 或 ExtensionInfo。",
                sourceExtensionName);
            return emptyList();
        }

        // 3. 根据消息类型获取对应的路由规则列表
        List<RoutingRuleDefinition> rules;
        switch (message.getType()) {
            case CMD:
                rules = sourceExtInfo.getMsgDestInfo().getCommandRules();
                break;
            case DATA:
                rules = sourceExtInfo.getMsgDestInfo().getDataRules();
                break;
            case VIDEO_FRAME:
                rules = sourceExtInfo.getMsgDestInfo().getVideoFrameRules();
                break;
            case AUDIO_FRAME:
                rules = sourceExtInfo.getMsgDestInfo().getAudioFrameRules();
                break;
            default:
                log.debug("DefaultExtensionMessageDispatcher: 消息类型 {} 不需要通过图配置进行路由。",
                    message.getType());
                return emptyList();
        }

        if (isEmpty(rules)) {
            log.debug("DefaultExtensionMessageDispatcher: 源 Extension {} 没有为消息类型 {} 配置路由规则。",
                sourceExtensionName, message.getType());
            return emptyList();
        }

        List<Location> determinedLocations = new java.util.ArrayList<>();
        String appUri = engineExtensionContext.getEngine().getApp().getAppUri();
        String graphId = engineExtensionContext.getEngine().getGraphId();

        // 4. 遍历规则，查找匹配的 Destination
        for (RoutingRuleDefinition rule : rules) {
            // 消息名称匹配：如果规则定义了名称，则消息的名称必须与规则名称匹配。
            // 否则，如果规则名称为 null，则视为匹配成功（不进行名称过滤）。
            boolean nameMatches = StringUtils.isEmpty(rule.getName())
                || StringUtils.isNotEmpty(message.getName()) && rule.getName().equals(message.getName());

            if (nameMatches && isNotEmpty(rule.getDestinations())) { // 修正：getDest() -> getDestinations()
                for (DestinationInfo destInfo : rule.getDestinations()) { // 修正：getDest() -> getDestinations()
                    // 将 DestinationInfo 转换为 Location
                    // 假设目的地在同一个 App 和 Graph 内，只关心 ExtensionName
                    // 复杂的跨 App/Graph 路由在 Engine/App 层面处理
                    String targetExtensionName = destInfo.getExtensionName(); // 修正：getExtension() -> getExtensionName()
                    if (StringUtils.isNotEmpty(targetExtensionName)) {
                        determinedLocations.add(new Location(appUri, graphId, targetExtensionName));
                    } else {
                        log.warn("DefaultExtensionMessageDispatcher: 路由规则中目的地 Extension 名称为空，规则: {}",
                            rule);
                    }
                }
            }
        }
        log.debug("DefaultExtensionMessageDispatcher: 根据图配置为消息 {} (Name: {}, Type: {}) 确定了 {} 个目的地。",
            message.getId(), message.getName(), message.getType(), determinedLocations.size());
        return determinedLocations;
    }
}