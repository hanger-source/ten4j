package source.hanger.core.engine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
    private final ConcurrentHashMap<String, ExtensionThread> extensionThreads;

    // 新增：存储 ExtensionGroup 实例
    private final ConcurrentHashMap<String, ExtensionGroup> extensionGroups;

    // 存储 Extension 的 Method 映射，优化反射调用性能
    private final ConcurrentHashMap<Class<? extends Extension>, Map<String, Method>> extensionMethodCache = new ConcurrentHashMap<>();

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

        extensionThreads = new ConcurrentHashMap<>();
        extensionGroups = new ConcurrentHashMap<>(); // 初始化 extensionGroups
        roleName = "ExtensionContext-%s".formatted(engine.getGraphId());

        log.info("ExtensionContext created for Engine: {}", engine.getGraphId());
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
            ExtensionThread newThread = new ExtensionThread("ExtensionThread-%s".formatted(k), this);
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
            log.error("Failed to find extension class {}: {}", extensionAddonName,
                    e.getMessage(), e);
            throw new RuntimeException("Extension class not found: %s".formatted(extensionAddonName),
                    e);
        }

        Extension extension = ReflectionUtils.newInstance(extensionClass);

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
                new AllMessageDestInfo() // 传入 AllMessageDestInfo 实例
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
            log.info("Extension {} successfully added to ExtensionGroup {}.", extensionName, extensionGroupName);
            // 在 ExtensionGroup 成功添加 Extension 后，触发 ExtensionGroup 的 onCreateExtensions
            // 此时，ExtensionGroup 会负责调用其内部 Extension 的生命周期方法
            targetGroup.onCreateExtensions(targetGroup.getTenEnv());
        } else {
            log.error("ExtensionGroup {} not found for Extension {}. Cannot manage extension.", extensionGroupName,
                    extensionName);
            throw new RuntimeException("ExtensionGroup not found: " + extensionGroupName);
        }

        log.info("Extension {} (Type: {}) loaded for Engine {} on ExtensionThread {} (Group: {}).",
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
            log.warn("ExtensionContext: ExtensionGroup {} 已经存在，不再重复加载。", existingGroup);
            return existingGroup;
        }

        // 获取或创建 ExtensionThread。每个 ExtensionGroup 将对应一个 ExtensionThread。
        // 这里假设 ExtensionGroup 的名称直接对应 ExtensionThread 的名称。
        ExtensionThread extensionThread = extensionThreads.computeIfAbsent(extensionGroupName, k -> {
            ExtensionThread newThread = new ExtensionThread("ExtensionThread-%s".formatted(k), this);
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

        log.info("ExtensionGroup {} (Addon: {}) loaded for Engine {} on ExtensionThread {}.",
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
            log.warn("ExtensionGroup for Extension {} not found. Cannot unload Extension.", extensionName);
            return;
        }

        // 获取关联的 ExtensionThread
        ExtensionThread extensionThread = extensionThreads.get(targetGroup.getName());
        if (extensionThread == null) {
            log.error("Associated ExtensionThread for group {} not found. Cannot unload Extension {}.",
                    targetGroup.getName(), extensionName);
            return;
        }

        // 委托 ExtensionGroup 移除 Extension
        extensionThread.getRunloop().postTask(() -> {
            targetGroup.removeExtension(extensionName);
            log.info("Extension {} removed from ExtensionGroup {}.", extensionName, targetGroup.getName());

            // 如果 ExtensionGroup 不再管理任何 Extension，则关闭其关联的 ExtensionThread
            if (targetGroup.getExtensions().isEmpty()) {
                extensionGroups.remove(targetGroup.getName());
                extensionThreads.remove(targetGroup.getName());
                extensionThread.close();
                log.info("ExtensionGroup {} is empty. Associated ExtensionThread {} closed.",
                        targetGroup.getName(), extensionThread.getThreadName());
            }
        });

        log.info("Extension {} unload request processed.", extensionName);
    }

    /**
     * 卸载所有已加载的 Extension 实例。
     */
    public void unloadAllExtensions() {
        log.info("ExtensionContext: Unloading all extensions for Engine {}.", engine.getGraphId());
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
        log.info("ExtensionContext: All extensions unloaded for Engine {}.", engine.getGraphId());
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
            return (Class<? extends Extension>) clazz;
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
        if (extensionName == null || extensionName.isEmpty()) {
            log.warn("ExtensionContext: 目标 Extension ID 为空或 null，无法分发消息 {}.getId()。跳过分发。",
                    message.getId());
            return;
        }

        // 查找目标 Extension 所在的 ExtensionThread
        ExtensionThread extensionThread = findExtensionThreadForExtension(extensionName);
        if (extensionThread == null) {
            log.error("ExtensionContext: 未找到 ExtensionThread 来处理 Extension {} 的消息 {} (Type: {}).",
                    extensionName, message.getId(), message.getType());
            if (message instanceof Command command) {
                routeCommandResultFromExtension(
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
            log.warn("ExtensionContext: ExtensionThread {} 没有关联 ExtensionGroup，无法执行消息转换。忽略消息 {}.",
                    extensionThread.getThreadName(), message.getId());
            return;
        }
        ExtensionInfo targetExtInfo = targetGroup.getExtensionInfo(extensionName);
        if (targetExtInfo != null && targetExtInfo.getMsgConversionContexts() != null) {
            for (MessageConversionContext context : targetExtInfo.getMsgConversionContexts()) {
                Message converted = MessageConverter.convertMessage(processedMessage, context);
                if (converted != processedMessage) { // 如果消息被转换了
                    processedMessage = converted;
                    log.debug("ExtensionContext: 消息 {} 被转换。", processedMessage.getId());
                }
            }
        }
        // --- 消息转换逻辑结束 ---

        // 将处理后的消息委托给 ExtensionThread 进行分发
        Message finalProcessedMessage = processedMessage;
        extensionThread.dispatchMessage(finalProcessedMessage, extensionName);
    }

    /**
     * 将命令提交给 Extension 进行处理。
     * 这是 Engine 消息分发到 Extension 的入口点，消息将在 Extension 专属线程上处理。
     * 注意：这里假设命令是点对点发送给特定的 Extension，而不是广播。
     *
     * @param command           要处理的命令。
     * @param targetExtensionId 目标 Extension 的 ID。
     */
    public void dispatchCommandToExtension(Command command, String targetExtensionId) {
        if (targetExtensionId == null || targetExtensionId.isEmpty()) {
            log.warn("ExtensionContext: 目标 Extension ID 为空或 null，无法分发命令 {}.getId()。跳过分发。",
                    command.getId());
            return;
        }

        // 查找目标 Extension 所在的 ExtensionThread
        ExtensionThread extensionThread = findExtensionThreadForExtension(targetExtensionId);
        if (extensionThread == null) {
            log.error("ExtensionContext: 未找到 ExtensionThread 来处理 Extension {} 的命令 {} (Type: {}).",
                    targetExtensionId, command.getId(), command.getType());
            if (command.getOriginalCommandId() != null) {
                routeCommandResultFromExtension(
                        CommandResult.fail(command,
                                "ExtensionThread for Extension not found: %s".formatted(targetExtensionId)),
                        command.getSrcLoc().getExtensionName() != null ? command.getSrcLoc().getExtensionName()
                                : "Engine"); // Engine 作为发送方
            }
            return;
        }

        // --- 消息转换逻辑开始 (对于入站命令) ---
        Message processedCommand = command;
        // 从 ExtensionGroup 中获取 ExtensionInfo 进行消息转换
        ExtensionGroup targetGroup = extensionThread.getExtensionGroup(); // 获取 ExtensionThread 关联的 ExtensionGroup
        if (targetGroup == null) {
            log.warn("ExtensionContext: ExtensionThread {} 没有关联 ExtensionGroup，无法执行消息转换。忽略命令 {}.",
                    extensionThread.getThreadName(), command.getId());
            return;
        }
        // 从 ExtensionGroup 中获取 ExtensionInfo
        ExtensionInfo targetExtInfo = targetGroup.getExtensionInfo(targetExtensionId);
        if (targetExtInfo != null && targetExtInfo.getMsgConversionContexts() != null) {
            for (MessageConversionContext context : targetExtInfo.getMsgConversionContexts()) {
                Message converted = MessageConverter.convertMessage(processedCommand, context);
                if (converted != processedCommand) { // 如果消息被转换了
                    processedCommand = converted;
                    log.debug("ExtensionContext: 入站命令 {} 被转换。", processedCommand.getId());
                }
            }
        }
        // --- 消息转换逻辑结束 ---

        // 将处理后的命令委托给 ExtensionThread 进行分发
        Command finalProcessedCommand = (Command) processedCommand;
        extensionThread.dispatchCommand(finalProcessedCommand, targetExtensionId);
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

    // 实现 ExtensionCommandSubmitter 接口方法
    @Override
    public CompletableFuture<CommandResult> submitCommandFromExtension(Command command, String sourceExtensionName) {
        // 命令从 Extension 提交，委托给 Engine 的 commandSubmitter
        log.debug("ExtensionContext: Extension {} 提交命令 {} 到 Engine。", sourceExtensionName, command.getId());
        // 修改 srcLoc 以反映真实的来源 Extension
        command.getSrcLoc().setExtensionName(sourceExtensionName);
        return engineCommandSubmitter.submitCommand(command);
    }

    // 实现 ExtensionMessageSubmitter 接口方法
    @Override
    public void submitMessageFromExtension(Message message, String sourceExtensionName) {
        // 消息从 Extension 提交，委托给 Engine 的 messageSubmitter
        log.debug("ExtensionContext: Extension {} 提交消息 {} 到 Engine。", sourceExtensionName, message.getId());
        // 修改 srcLoc 以反映真实的真实来源 Extension
        message.getSrcLoc().setExtensionName(sourceExtensionName);
        engineMessageSubmitter.submitInboundMessage(message, null); // 传入 null 作为 connection 参数
    }

    // 实现 ExtensionCommandSubmitter 接口中新增的方法
    @Override
    public void routeCommandResultFromExtension(CommandResult commandResult, String sourceExtensionName) {
        log.debug("ExtensionContext: Extension {} 路由命令结果 {} 到 Engine。", sourceExtensionName,
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
}