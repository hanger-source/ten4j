package source.hanger.core.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import source.hanger.core.app.App;
import source.hanger.core.extension.Extension;
import source.hanger.core.extension.ExtensionEnvImpl;
import source.hanger.core.extension.ExtensionGroup;
import source.hanger.core.extension.ExtensionGroupInfo;
import source.hanger.core.extension.ExtensionInfo;
import source.hanger.core.extension.ExtensionThread;
import source.hanger.core.extension.submitter.ExtensionCommandSubmitter;
import source.hanger.core.extension.submitter.ExtensionMessageSubmitter;
import source.hanger.core.graph.AllMessageDestInfo;
import source.hanger.core.graph.DestinationInfo;
import source.hanger.core.graph.RoutingRuleDefinition;
import source.hanger.core.message.CommandExecutionHandle;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageConversionContext;
import source.hanger.core.message.command.Command;
import source.hanger.core.path.PathTable;
import source.hanger.core.util.MessageConverter;
import source.hanger.core.util.ReflectionUtils;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.apache.commons.collections4.CollectionUtils.*;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

/**
 * 管理 Engine 中 Extension 的生命周期和交互。
 * 这是 Engine 与其加载的 Extension 之间交互的主要接口。
 */
@Slf4j
public class EngineExtensionContext implements ExtensionCommandSubmitter, ExtensionMessageSubmitter {

    @Getter
    private final Engine engine; // 引擎引用
    // <-- 新增方法
    @Getter
    private final App app; // 应用引用
    @Getter
    private final PathTable pathTable;
    private final MessageSubmitter engineMessageSubmitter;
    private final CommandSubmitter engineCommandSubmitter;

    // 新增：存储 ExtensionThread 实例，key 可以是 ExtensionGroup 的名称或其他逻辑分组 ID
    private final Map<String, ExtensionThread> extensionThreads;

    // 新增：存储 ExtensionGroup 实例
    private final Map<String, ExtensionGroup> extensionGroups;

    @Getter
    private final String roleName;

    public EngineExtensionContext(Engine engine, App app, PathTable pathTable, MessageSubmitter engineMessageSubmitter,
        CommandSubmitter engineCommandSubmitter) {
        this.engine = Objects.requireNonNull(engine, "Engine must not be null.");
        this.app = Objects.requireNonNull(app, "App must not be null.");
        this.pathTable = Objects.requireNonNull(pathTable, "PathTable must not be null.");
        this.engineMessageSubmitter = Objects.requireNonNull(engineMessageSubmitter,
            "Engine message submitter must not be null.");
        this.engineCommandSubmitter = Objects.requireNonNull(engineCommandSubmitter,
            "Engine command submitter must not be null.");

        extensionThreads = new HashMap<>();
        extensionGroups = new HashMap<>();
        roleName = "ExtensionContext-%s".formatted(engine.getGraphId());

        EngineExtensionContext.log.info("ExtensionContext created for Engine: {}", engine.getGraphId());
    }

    /**
     * 加载并初始化一个 Extension 实例。
     *
     * @param extensionName      Extension 的唯一 ID。
     * @param extensionAddonName Extension 的类型。
     * @param extensionGroupName Extension 的分组。
     * @param property           Extension 的配置。
     * @return 加载的 Extension 实例。
     */
    public Extension loadExtension(String extensionName, String extensionAddonName,
        String extensionGroupName, Map<String, Object> property) {

        // 获取或创建 ExtensionThread。每个 ExtensionGroup 将对应一个 ExtensionThread。
        ExtensionThread extensionThread = extensionThreads.computeIfAbsent(extensionGroupName, k -> {
            ExtensionThread newThread = new ExtensionThread("ExtensionThread[%s]".formatted(k), this);
            newThread.start(); // 启动线程，以便其 Runloop 可用
            return newThread;
        });

        // 获取或创建 ExtensionGroup。
        extensionGroups.computeIfAbsent(extensionGroupName, k -> {
            // 在创建 ExtensionGroup 时，为它创建一个 ExtensionGroupInfo 实例
            // 使用所有参数的构造函数，为默认情况提供占位符或 null
            ExtensionGroupInfo groupInfo = new ExtensionGroupInfo(
                // extensionGroupAddonName - 默认 ExtensionGroup 没有特定的 addon
                "default_extension_group", // 或从配置中获取
                extensionGroupName, // extensionGroupInstanceName
                new Location(null, null, null),
                emptyMap() // property (可以为空 map)
            );

            ExtensionGroup newGroup = new ExtensionGroup(extensionGroupName, groupInfo);
            extensionThread.setExtensionGroup(newGroup);

            // 为 ExtensionGroup 创建并设置 TenEnv，使用 ExtensionThread 的 Runloop
            ExtensionEnvImpl extensionGroupEnv = new ExtensionEnvImpl(null, this, this,
                extensionThread.getRunloop(), this, groupInfo);
            newGroup.setTenEnv(extensionGroupEnv);

            // 【新增】调用 ExtensionGroup 的 onConfigure 和 onInit 方法
            extensionGroupEnv.onConfigure(emptyMap()); // ExtensionGroup 的 configure 阶段可以接收配置
            extensionGroupEnv.onInit();

            return newGroup;
        });

        // 1. 创建 Extension 实例
        Class<? extends Extension> extensionClass;
        try {
            extensionClass = findExtensionClass(extensionAddonName); // 从 extInfo 获取 extensionType
        } catch (ClassNotFoundException e) {
            EngineExtensionContext.log.error("Failed to find extension class {}: {}", extensionAddonName,
                e.getMessage(), e);
            throw new RuntimeException("Extension class not found: %s".formatted(extensionAddonName),
                e);
        }

        Extension extension = ReflectionUtils.newInstance(extensionClass);

        // 根据 GraphDefinition 过滤并构建 AllMessageDestInfo
        AllMessageDestInfo messageDestInfo = new AllMessageDestInfo();
        engine.getGraphDefinition().getConnections().stream()
            .filter(conn -> conn.getExtension() != null && conn.getExtension().equals(extensionName))
            .forEach(conn -> {
                if (conn.getCmd() != null) {
                    messageDestInfo.setCommandRules(conn.getCmd());
                }
                if (conn.getData() != null) {
                    messageDestInfo.setDataRules(conn.getData());
                }
                if (conn.getVideoFrame() != null) {
                    messageDestInfo.setVideoFrameRules(conn.getVideoFrame());
                }
                if (conn.getAudioFrame() != null) {
                    messageDestInfo.setAudioFrameRules(conn.getAudioFrame());
                }
            });

        // 【新增】构建 ExtensionInfo 实例
        ExtensionInfo extInfo = new ExtensionInfo(
            extensionAddonName, // addonName
            extensionName, // extensionInstanceName
            extensionGroupName, // extensionGroupName
            new Location(
                app.getAppUri(), // appUri
                engine.getGraphId(), // graphId
                extensionName // extensionName
            ),
            property != null ? property : emptyMap(), // property
            emptyList(), // msgConversionContexts (暂时为空，或从 property 解析)
            messageDestInfo // 传入填充好的 AllMessageDestInfo 实例
        );

        // 创建 ExtensionEnvImpl 用于单个 Extension
        // 这个 ExtensionEnv 使用 EngineExtensionContext 作为其提交器，并使用 ExtensionThread 的
        // Run loop。
        ExtensionEnvImpl extensionEnv = new ExtensionEnvImpl(
            extension, // 传入 Extension 实例
            this, // commandSubmitter
            this, // messageSubmitter
            extensionThread.getRunloop(), // run loop
            this, // extensionContext
            extInfo // 传入 ExtensionInfo 对象
        ); // 调用 Extension 的生命周期方法 (通过 ExtensionEnvImpl 调用)

        // 将 Extension 添加到其所属的 ExtensionGroup (而不是 ExtensionThread)
        ExtensionGroup targetGroup = extensionGroups.get(extensionGroupName);
        if (targetGroup != null) {
            targetGroup.addManagedExtension(extensionName, extension, extensionEnv, extInfo);
            EngineExtensionContext.log.info("Extension {} successfully added to ExtensionGroup {}.", extensionName,
                extensionGroupName);
        } else {
            EngineExtensionContext.log.error("ExtensionGroup {} not found for Extension {}. Cannot manage extension.",
                extensionGroupName,
                extensionName);
            throw new RuntimeException("ExtensionGroup not found: %s".formatted(extensionGroupName));
        }

        EngineExtensionContext.log.info(
            "Extension {} (Type: {}) loaded for Engine {} on ExtensionThread {} (Group: {}).",
            extensionName, extensionAddonName, engine.getGraphId(),
            extensionThread.getThreadName(), extensionGroupName);

        return extension;
    }

    /**
     * 加载并初始化一个 ExtensionGroup 实例。
     *
     * @param extensionGroupName      ExtensionGroup 的唯一 ID (实例名称)。
     * @param extensionGroupAddonName ExtensionGroup 对应的 Addon 名称。
     * @param property                ExtensionGroup 的配置属性。
     */
    public ExtensionGroup loadExtensionGroup(String extensionGroupName, String extensionGroupAddonName,
        Map<String, Object> property) {
        // 检查是否已存在同名的 ExtensionGroup
        ExtensionGroup existingGroup = extensionGroups.get(extensionGroupName);
        if (existingGroup != null) {
            EngineExtensionContext.log.warn("ExtensionContext: ExtensionGroup {} 已经存在，不再重复加载。",
                existingGroup);
            return existingGroup;
        }

        // 获取或创建 ExtensionThread。每个 ExtensionGroup 将对应一个 ExtensionThread。
        // 这里假设 ExtensionGroup 的名称直接对应 ExtensionThread 的名称。
        ExtensionThread extensionThread = extensionThreads.computeIfAbsent(extensionGroupName, k -> {
            ExtensionThread newThread = new ExtensionThread("ExtensionThread[%s]".formatted(k), this);
            newThread.start(); // 启动线程，以便其 Runloop 可用
            return newThread;
        });

        // 创建 ExtensionGroupInfo 实例
        ExtensionGroupInfo groupInfo = new ExtensionGroupInfo(
            extensionGroupAddonName,
            extensionGroupName,
            new Location(null, null, null), // loc (可以为空或默认值)
            property != null ? property : emptyMap() // property (可以为空 map)
        );

        // 创建 ExtensionGroup 实例
        ExtensionGroup newGroup = new ExtensionGroup(extensionGroupName, groupInfo);

        // 将 ExtensionGroup 设置到 ExtensionThread
        extensionThread.setExtensionGroup(newGroup);

        // 为 ExtensionGroup 创建并设置 TenEnv，使用 ExtensionThread 的 Runloop
        ExtensionEnvImpl extensionGroupEnv = new ExtensionEnvImpl(
            null, // ExtensionGroup 本身不是 Extension
            this, // commandSubmitter
            this, // messageSubmitter
            extensionThread.getRunloop(), // runloop
            this, // extensionContext
            groupInfo // 传入 ExtensionGroupInfo 实例
        );
        newGroup.setTenEnv(extensionGroupEnv);

        // 调用 ExtensionGroup 的生命周期方法 (通过 ExtensionEnvImpl 调用)
        extensionGroupEnv.onConfigure(groupInfo.getProperty()); // 将 property 传递给 configure
        extensionGroupEnv.onInit();

        extensionGroups.put(extensionGroupName, newGroup); // 添加到管理列表

        EngineExtensionContext.log.info("ExtensionGroup {} (Addon: {}) loaded for Engine {} on ExtensionThread {}.",
            extensionGroupName, extensionGroupAddonName, engine.getGraphId(), extensionThread.getThreadName());

        return newGroup;
    }

    /**
     * 卸载并销毁一个 Extension 实例。
     *
     * @param extensionName Extension 的唯一 ID。
     */
    public void unloadExtension(String extensionName) {
        // 找到包含该 Extension 的 ExtensionGroup
        ExtensionGroup targetGroup = extensionGroups.values().stream()
            .filter(group -> group.getExtensions().containsKey(extensionName))
            .findFirst()
            .orElse(null);

        if (targetGroup == null) {
            EngineExtensionContext.log.warn("ExtensionGroup for Extension {} not found. Cannot unload Extension.",
                extensionName);
            return;
        }

        // 获取关联的 ExtensionThread
        ExtensionThread extensionThread = extensionThreads.get(targetGroup.getName());
        if (extensionThread == null) {
            EngineExtensionContext.log.error(
                "Associated ExtensionThread for group {} not found. Cannot unload Extension {}.",
                targetGroup.getName(), extensionName);
            return;
        }

        // 委托 ExtensionGroup 移除 Extension
        extensionThread.getRunloop().postTask(() -> {
            targetGroup.removeExtension(extensionName);
            EngineExtensionContext.log.info("Extension {} removed from ExtensionGroup {}.", extensionName,
                targetGroup.getName());

            // 如果 ExtensionGroup 不再管理任何 Extension，则关闭其关联的 ExtensionThread
            if (targetGroup.getExtensions().isEmpty()) {
                extensionGroups.remove(targetGroup.getName());
                extensionThreads.remove(targetGroup.getName());
                extensionThread.close();
                EngineExtensionContext.log.info("ExtensionGroup {} is empty. Associated ExtensionThread {} closed.",
                    targetGroup.getName(), extensionThread.getThreadName());
            }
        });

        EngineExtensionContext.log.info("Extension {} unload request processed.", extensionName);
    }

    /**
     * 卸载所有已加载的 Extension 实例。
     */
    public void unloadAllExtensions() {
        EngineExtensionContext.log.info("ExtensionContext: Unloading all extensions for Engine {}.",
            engine.getGraphId());
        // 创建一个副本以避免并发修改异常
        List<String> extensionGroupNames = new ArrayList<>(extensionGroups.keySet());
        for (String groupName : extensionGroupNames) {
            ExtensionGroup extensionGroup = extensionGroups.get(groupName);
            if (extensionGroup != null) {
                // 遍历 ExtensionGroup 中所有的 Extension 并卸载它们
                List<String> extensionNamesToUnload = new ArrayList<>(extensionGroup.getExtensions().keySet());
                for (String extensionName : extensionNamesToUnload) {
                    unloadExtension(extensionName); // 调用单点卸载方法
                }
            }
        }
        EngineExtensionContext.log.info("ExtensionContext: All extensions unloaded for Engine {}.",
            engine.getGraphId());
    }

    /**
     * 根据 Extension 类型名称查找对应的 Class。
     *
     * @param extensionType Extension 的类型名称
     * @return 对应的 Class 对象。
     * @throws ClassNotFoundException 如果找不到对应的 Class。
     */
    @SuppressWarnings("unchecked")
    private Class<? extends Extension> findExtensionClass(String extensionType) throws ClassNotFoundException {
        // 假设 Extension 类都在 classpath 中
        // Note: 更复杂的 Extension 发现机制，例如通过插件系统，是未来可考虑的增强。
        Class<?> clazz = Class.forName(extensionType);
        if (Extension.class.isAssignableFrom(clazz)) {
            return (Class<? extends Extension>)clazz;
        } else {
            throw new IllegalArgumentException("Class %s is not an Extension.".formatted(extensionType));
        }
    }

    /**
     * 将消息提交给 Extension 进行处理。
     * 这是 Engine 消息分发到 Extension 的入口点，消息将在 Extension 专属线程上处理。
     *
     * @param message       待分发的消息。
     * @param extensionName 目标 Extension 的 ID。
     */
    public void dispatchMessageToExtension(Message message, String extensionName) {
        if (StringUtils.isEmpty(extensionName)) {
            EngineExtensionContext.log.warn(
                "ExtensionContext: 目标 Extension ID 为空或 null，无法分发消息 {}.getId()。跳过分发。",
                message.getId());
            return;
        }

        // 查找目标 Extension 所在的 ExtensionThread
        ExtensionThread extensionThread = findExtensionThreadForExtension(extensionName);
        if (extensionThread == null) {
            EngineExtensionContext.log.error(
                "ExtensionContext: 未找到 ExtensionThread 来处理 Extension {} 的消息 {} (Type: {}).",
                extensionName, message.getId(), message.getType());
            if (message instanceof Command command) {
                submitCommandResultFromExtension(
                    CommandResult.fail(command,
                        "ExtensionThread for Extension not found: %s".formatted(extensionName)),
                    command.getSrcLoc().getExtensionName() != null ? command.getSrcLoc().getExtensionName()
                        : "Engine"); // 使用 command.getSrcLoc().getExtensionName() 作为 sourceExtensionName
            }
            return;
        }

        // --- 消息转换逻辑开始 ---
        Message processedMessage = message;
        // 从 ExtensionGroup 中获取 ExtensionInfo 进行消息转换
        ExtensionGroup targetGroup = extensionThread.getExtensionGroup();
        if (targetGroup == null) {
            EngineExtensionContext.log.warn(
                "ExtensionContext: ExtensionThread {} 没有关联 ExtensionGroup，无法执行消息转换。忽略消息 {}.",
                extensionThread.getThreadName(), message.getId());
            return;
        }
        ExtensionInfo targetExtInfo = targetGroup.getExtensionInfo(extensionName);
        if (targetExtInfo != null && targetExtInfo.getMsgConversionContexts() != null) {
            for (MessageConversionContext context : targetExtInfo.getMsgConversionContexts()) {
                Message converted = MessageConverter.convertMessage(processedMessage, context);
                if (converted != processedMessage) { // 如果消息被转换了
                    processedMessage = converted;
                    EngineExtensionContext.log.debug("ExtensionContext: 消息 {} 被转换。", processedMessage.getId());
                }
            }
        }
        // --- 消息转换逻辑结束 ---

        // 将处理后的消息委托给 ExtensionThread 进行分发
        Message finalProcessedMessage = processedMessage;
        extensionThread.dispatchMessage(finalProcessedMessage, extensionName);
    }

    // 辅助方法：根据 ExtensionId 查找其所属的 ExtensionThread
    // Note: 目前假设一个 ExtensionThread 管理一个 Extension，后续可根据 ExtensionGroup 逻辑调整
    private ExtensionThread findExtensionThreadForExtension(String extensionName) {
        return extensionGroups.values().stream()
            .filter(extensionGroup -> extensionGroup.getExtensions().containsKey(extensionName))
            .map(ExtensionGroup::getName)
            .map(extensionThreads::get)
            .findAny().orElse(null);
    }

    // 获取 Extension 实例 (通过 ExtensionGroup)
    public Extension getExtension(String extensionId) {
        // 遍历所有 ExtensionGroup 查找包含该 Extension 的组
        for (ExtensionGroup group : extensionGroups.values()) {
            Extension extension = group.getExtension(extensionId);
            if (extension != null) {
                return extension;
            }
        }
        return null;
    }

    // 辅助方法，用于在需要时从 ExtensionGroup 获取 ExtensionEnvImpl
    public ExtensionEnvImpl getExtensionEnv(String extensionId) {
        // 遍历所有 ExtensionGroup 查找包含该 Extension 的组
        for (ExtensionGroup group : extensionGroups.values()) {
            ExtensionEnvImpl env = group.getManagedExtensionEnv(extensionId);
            if (env != null) {
                return env;
            }
        }
        return null;
    }

    /**
     * 根据 Extension 名称获取 ExtensionInfo 实例。
     * 此方法是为了方便外部（例如 DefaultExtensionMessageDispatcher）获取 Extension 的运行时信息。
     *
     * @param extensionName Extension 的名称。
     * @return 对应的 ExtensionInfo 实例，如果未找到则返回 null。
     */
    public ExtensionInfo getExtensionInfo(String extensionName) {
        // 首先找到 Extension 所在的 ExtensionGroup
        ExtensionGroup targetGroup = extensionGroups.values().stream()
            .filter(group -> group.getExtensionInfos().containsKey(extensionName))
            .findFirst()
            .orElse(null);

        if (targetGroup != null) {
            return targetGroup.getExtensionInfo(extensionName);
        }
        return null;
    }

    // 实现 ExtensionCommandSubmitter 接口方法
    @Override
    public CommandExecutionHandle<CommandResult> submitCommandFromExtension(Command command, String sourceExtensionName) {
        // 命令从 Extension 提交，委托给 Engine 的 commandSubmitter
        log.info("[{}] ExtensionContext: 提交命令 {} {} {} 到 Engine。",
            sourceExtensionName,
            command.getId(), command.getName(), command.getProperties());
        // 修改 srcLoc 以反映真实的真实来源 Extension
        if (command.getSrcLoc() == null) {
            command.setSrcLoc(new Location(app.getAppUri(), engine.getGraphId(), sourceExtensionName));
        } else {
            command.getSrcLoc().setGraphId(engine.getGraphId()).setExtensionName(sourceExtensionName);
        }
        command.getSrcLoc().setExtensionName(sourceExtensionName);
        // 确定消息目的地
        if (command.getDestLocs() == null) {
            command.setDestLocs(determineMessageDestinationsFromGraph(command));
        }
        return engineCommandSubmitter.submitCommandWithResultHandle(command);
    }

    // 实现 ExtensionMessageSubmitter 接口方法
    @Override
    public void submitMessageFromExtension(Message message, String sourceExtensionName) {
        // 消息从 Extension 提交，委托给 Engine 的 messageSubmitter
        EngineExtensionContext.log.debug("ExtensionContext: Extension {} 提交消息 {} 到 Engine。", sourceExtensionName,
            message.getId());
        // 修改 srcLoc 以反映真实的真实来源 Extension
        if (message.getSrcLoc() == null) {
            message.setSrcLoc(new Location(app.getAppUri(), engine.getGraphId(), sourceExtensionName));
        } else {
            message.getSrcLoc().setGraphId(engine.getGraphId()).setExtensionName(sourceExtensionName);
        }
        Location srcLoc = message.getSrcLoc();
        if (CollectionUtils.isNotEmpty(message.getDestLocs())) {
            List<Location> selfDestLocs = message.getDestLocs().stream().filter(srcLoc::equals).toList();
            if (CollectionUtils.isNotEmpty(selfDestLocs)) {
                message.getDestLocs().removeIf(loc -> loc.equals(srcLoc));
                log.warn("ExtensionContext: 忽略消息 {} 的目的地 {}，因为消息的源和目的地相同。", message.getId(), srcLoc);
            }
        }
        // 确定消息目的地
        if (isEmpty(message.getDestLocs())) {
            message.setDestLocs(determineMessageDestinationsFromGraph(message));
        }
        engineMessageSubmitter.submitInboundMessage(message, null); // 传入 null 作为 connection 参数
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
        ExtensionInfo sourceExtInfo = getExtensionInfo(sourceExtensionName);
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
        String appUri = getEngine().getApp().getAppUri();
        String graphId = getEngine().getGraphId();

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

    // 实现 ExtensionCommandSubmitter 接口中新增的方法
    @Override
    public void submitCommandResultFromExtension(CommandResult commandResult, String sourceExtensionName) {
        EngineExtensionContext.log.debug("ExtensionContext: Extension {} 路由命令结果 {} 到 Engine。",
            sourceExtensionName,
            commandResult.getId());
        // 委托给 Engine 处理，Engine 知道如何路由结果
        // 这里需要 Engine 提供一个方法来路由来自 Extension 的命令结果
        engine.submitCommandResult(commandResult); // 修正：直接调用 submitCommandResult
    }

    // 新增：获取当前 EngineContext 中所有 Extension 的 ExtensionInfo 列表
    public List<ExtensionInfo> getAllExtensionInfos() {
        List<ExtensionInfo> allExtInfos = new ArrayList<>();
        extensionGroups.values().forEach(group -> {
            group.getExtensionInfos().values().forEach(extInfo -> {
                allExtInfos.add(extInfo);
            });
        });
        return allExtInfos;
    }

    // 新增：获取当前 EngineContext 中所有 ExtensionGroup 的 ExtensionGroupInfo 列表
    public List<ExtensionGroupInfo> getAllExtensionGroupInfos() {
        return new ArrayList<>(extensionGroups.values().stream()
            .map(ExtensionGroup::getExtensionGroupInfo)
            .filter(Objects::nonNull) // 过滤掉 null
            .toList());
    }

    /**
     * 触发所有已加载的 ExtensionGroup 的 onCreateExtensions 方法。
     * 此方法通常在 Engine 启动时调用，以确保所有 ExtensionGroup 都已准备好其扩展。
     */
    public void startExtensionGroups() { // 重命名方法
        EngineExtensionContext.log.info("ExtensionContext: Triggering onCreateExtensions for all loaded ExtensionGroups.");
        extensionGroups.values().forEach(extensionGroup -> {
            EngineExtensionContext.log.info("ExtensionContext: Triggering onCreateExtensions for ExtensionGroup: {}",
                extensionGroup.getName());
            extensionGroup.onCreateExtensions(extensionGroup.getTenEnv());
        });
        EngineExtensionContext.log.info("ExtensionContext: Finished triggering onCreateExtensions for all loaded ExtensionGroups.");
    }
}