package source.hanger.core.extension;

import java.util.concurrent.ConcurrentHashMap;

import source.hanger.core.engine.EngineExtensionContext;
import source.hanger.core.extension.ExtensionInfo;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.runloop.Runloop;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;

/**
 * `ExtensionThread` 模拟 C 端 `ten_extension_thread_t`，
 * 负责在一个专用线程上管理和执行一组 Extension 的生命周期和消息分发。
 * 每个 `ExtensionThread` 内部拥有一个 `Runloop` 来处理其线程上的异步任务。
 */
@Slf4j
public class ExtensionThread implements Agent {

    @Getter
    private final String threadName;
    @Getter
    private final Runloop runloop;
    @Getter
    private final ConcurrentHashMap<String, Extension> extensions;
    private final ConcurrentHashMap<String, ExtensionEnvImpl> extensionEnvs; // 存储 ExtensionEnvImpl 实例
    private final ConcurrentHashMap<String, ExtensionInfo> extensionInfos; // 存储 ExtensionInfo 实例
    @Setter
    private ExtensionGroup extensionGroup; // 新增：ExtensionGroup 实例
    private final EngineExtensionContext engineExtensionContext;

    public ExtensionThread(String threadName, EngineExtensionContext engineExtensionContext) {
        this.threadName = threadName;
        this.engineExtensionContext = engineExtensionContext;
        runloop = Runloop.createRunloop("%s-Runloop".formatted(threadName));
        extensions = new ConcurrentHashMap<>();
        extensionEnvs = new ConcurrentHashMap<>();
        extensionInfos = new ConcurrentHashMap<>();
        log.info("ExtensionThread {} created.", threadName);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // 实现 org.agrona.concurrent.Agent 接口
    // ----------------------------------------------------------------------------------------------------------------

    @Override
    public int doWork() {
        // ExtensionThread 的 doWork() 主要负责调度其管理的 Extension 的任务
        // 这里暂时不实现具体的调度逻辑，因为 Extension 的任务通过 Runloop.postTask 提交。
        // 如果未来需要 ExtensionThread 主动轮询 Extensions 的工作，可以在这里添加。
        return 0; // 返回 0 表示没有做任何工作
    }

    @Override
    public String roleName() {
        return "ExtensionThread-Agent-%s".formatted(threadName);
    }

    @Override
    public void onStart() {
        log.info("ExtensionThread Agent {} started.", roleName());
        // 在这里可以添加 ExtensionThread 启动时的 Agent 特有逻辑
    }

    @Override
    public void onClose() {
        log.info("ExtensionThread Agent {} closed.", roleName());
        // 在这里可以添加 ExtensionThread 关闭时的 Agent 特有逻辑
    }

    /**
     * 启动 Extension 线程，开始运行其内部的 Runloop。
     */
    public void start() {
        // Runloop 内部已经管理了线程，直接调用其 start 方法
        runloop.start();
        log.info("ExtensionThread {} started with dedicated Runloop.", threadName);

        // 调用 ExtensionGroup 的 onConfigure 和 onInit
        if (extensionGroup != null) {
            ExtensionEnvImpl extensionGroupEnv = new ExtensionEnvImpl(
                    null, // extension (ExtensionGroup 本身不是 Extension)
                    engineExtensionContext, // commandSubmitter
                    engineExtensionContext, // messageSubmitter
                    runloop, // runloop
                    engineExtensionContext, // extensionContext
                    null // ExtensionGroup 本身没有 ExtensionInfo
            );
            extensionGroup.setTenEnv(extensionGroupEnv); // 设置
            runloop.postTask(() -> {
                try {
                    extensionGroup.onConfigure(extensionGroupEnv);
                    extensionGroup.onInit(extensionGroupEnv);
                    log.info("ExtensionGroup {}: onConfigure/onInit completed.", extensionGroup.getName());
                } catch (Exception e) {
                    log.error("ExtensionGroup {}: Error during onConfigure/onInit: {}",
                            extensionGroup.getName(), e.getMessage(), e);
                }
            });
        }
    }

    /**
     * 关闭 Extension 线程及其内部的 Runloop。
     */
    public void close() {
        // Runloop 内部已经管理了线程的关闭，直接调用其 shutdown 方法
        log.info("ExtensionThread {}: Initiating shutdown of Runloop.", threadName);
        runloop.shutdown(); // 关闭 Runloop，停止接受新任务

        // 调用 ExtensionGroup 的 onDeinit
        if (extensionGroup != null
                && extensionGroup.getTenEnv() != null) {
            // 确保在 Runloop 线程中执行 onDeinit
            ExtensionEnvImpl extensionGroupEnv = (ExtensionEnvImpl) extensionGroup
                    .getTenEnv();
            runloop.postTask(() -> {
                try {
                    extensionGroup.onDeinit(extensionGroupEnv);
                    log.info("ExtensionGroup {}: onDeinit completed.", extensionGroup.getName());
                } catch (Exception e) {
                    log.error("ExtensionGroup {}: Error during onDeinit: {}",
                            extensionGroup.getName(), e.getMessage(), e);
                }
            });
        }

        // 清理 Extension 列表，但 Extension 的 destroy 应该在 removeExtension 中调用
        extensions.clear();
        extensionEnvs.clear();
        extensionInfos.clear();
        log.info("ExtensionThread {} closed.", threadName);
    }

    /**
     * 将 Extension 添加到此线程的管理列表，并执行其生命周期回调。
     * 确保此方法在 ExtensionThread 的 Runloop 线程上被调用。
     */
    public void addExtension(Extension extension, ExtensionEnvImpl extensionEnv, ExtensionInfo extInfo) {
        if (extensions.containsKey(extensionEnv.getExtensionName())) {
            log.warn("Extension {} already added to ExtensionThread {}.", extensionEnv.getExtensionName(),
                    threadName);
            return;
        }

        // 确保在 Runloop 线程中执行生命周期回调
        runloop.postTask(() -> {
            try {
                // 将 Extension 及其环境添加到 ExtensionGroup 进行管理
                if (extensionGroup != null) {
                    extensionGroup.addManagedExtension(extensionEnv.getExtensionName(), extension, extensionEnv,
                            extInfo); // <-- 新增调用

                    // 【新增】在 ExtensionGroup 成功添加 Extension 后，触发 ExtensionGroup 的 onCreateExtensions
                    // 此时，ExtensionGroup 会负责调用其内部 Extension 的生命周期方法
                    extensionGroup.onCreateExtensions(extensionGroup.getTenEnv()); // 传入 ExtensionGroup 的 TenEnv

                } else {
                    // 如果没有 ExtensionGroup，则直接管理（这在当前设计中不应该发生，或者表示是一个独立的 Extension）
                    // 为了保持当前功能，保留这部分逻辑，但建议重构以强制 Extension 属于一个 Group
                    extensions.put(extensionEnv.getExtensionName(), extension);
                    extensionEnvs.put(extensionEnv.getExtensionName(), extensionEnv);
                    extensionInfos.put(extensionEnv.getExtensionName(), extInfo);

                    log.warn(
                            "ExtensionThread {}: Extension {} is not associated with an ExtensionGroup. Handling directly.",
                            threadName, extensionEnv.getExtensionName());

                    // 【移除】直接调用 Extension 的生命周期回调
                    extension.onConfigure(extensionEnv, extInfo.getProperty());
                    extension.onInit(extensionEnv);
                    extension.onStart(extensionEnv);
                }

                log.info("ExtensionThread {}: Extension {} lifecycle (onConfigure/onInit/onStart) completed.",
                        threadName, extensionEnv.getExtensionName());

            } catch (Exception e) {
                log.error("ExtensionThread {}: Error adding Extension {} or during lifecycle callbacks: {}",
                        threadName, extensionEnv.getExtensionName(), e.getMessage(), e);
            }
        });
    }

    /**
     * 从此线程的管理列表移除 Extension，并执行其生命周期回调。
     * 确保此方法在 ExtensionThread 的 Runloop 线程上被调用。
     */
    public void removeExtension(String extensionName) {
        runloop.postTask(() -> {
            Extension extension = extensions.remove(extensionName);
            ExtensionEnvImpl extensionEnv = extensionEnvs.remove(extensionName);
            extensionInfos.remove(extensionName);

            if (extension != null) {
                log.info("ExtensionThread {}: Removing Extension {} from management.", threadName,
                        extensionName);
                try {
                    // 调用生命周期回调
                    extension.onStop(extensionEnv);
                    extension.onDeinit(extensionEnv);
                    extension.destroy(extensionEnv); // 最终销毁

                    // ExtensionGroup 的 onDestroyExtensions 将在这里被触发
                    if (extensionGroup != null) {
                        // 这里需要传递一个包含被销毁 Extension 的列表。目前简化为单个 Extension。
                        // 如果 C 端 on_destroy_extensions 接收一个 List<Extension>，这里需要调整。
                        // 暂时用 Collections.singletonList(extension) 或创建一个新的 List
                        extensionGroup.onDestroyExtensions(extensionGroup.getTenEnv(), // 【修正】传入 ExtensionGroup 的 TenEnv
                                java.util.Collections.singletonList(extension));
                    }

                    log.info(
                            "ExtensionThread {}: Extension {} lifecycle (onStop/onDeinit/destroy) completed.",
                            threadName, extensionEnv.getExtensionName());
                } catch (Exception e) {
                    log.error("ExtensionThread {}: Error removing Extension {} or during lifecycle callbacks: {}",
                            threadName, extensionName, e.getMessage(),
                            e);
                }
            } else {
                log.warn("ExtensionThread {}: Extension {} not found for removal.", threadName, extensionName);
            }
        });
    }

    /**
     * 将消息分发给此 ExtensionThread 上的目标 Extension。
     * 确保此方法可以从任何线程调用，并通过 Runloop 异步调度实际分发。
     */
    public void dispatchMessage(Message message, String targetExtensionId) {
        runloop.postTask(() -> {
            Extension extension = extensions.get(targetExtensionId);
            ExtensionEnvImpl extensionEnv = extensionEnvs.get(targetExtensionId);

            if (extension == null || extensionEnv == null) {
                log.error("ExtensionThread {}: 无法找到 Extension {} 或其环境，无法分发消息 {}.",
                        threadName, targetExtensionId, message.getId());
                // 如果是命令，需要返回一个失败的 CommandResult
                if (message instanceof Command command) {
                    // 这里需要一个机制将 CommandResult 回传给 Engine，可能通过 ExtensionContext
                    // 暂时省略，后续在 ExtensionContext 中处理。
                }
                return;
            }

            try {
                // 根据消息类型调用对应的 onXXX 回调
                switch (message.getType()) {
                    case CMD:
                        extension.onCmd(extensionEnv, (Command) message);
                        break;
                    case CMD_RESULT:
                        extension.onCmdResult(extensionEnv, (CommandResult) message);
                        break;
                    case DATA:
                        extension.onDataMessage(extensionEnv, (DataMessage) message);
                        break;
                    case AUDIO_FRAME:
                        extension.onAudioFrame(extensionEnv, (AudioFrameMessage) message);
                        break;
                    case VIDEO_FRAME:
                        extension.onVideoFrame(extensionEnv, (VideoFrameMessage) message);
                        break;
                    // 对于 CMD_CLOSE_APP, CMD_START_GRAPH, CMD_STOP_GRAPH 等 Engine/App 级别命令，
                    // ExtensionThread 不应该直接处理，它们应该在 Engine 级别被拦截和处理。
                    // 这里仅做警告，并对命令返回失败结果。
                    case CMD_CLOSE_APP:
                    case CMD_START_GRAPH:
                    case CMD_STOP_GRAPH:
                    case CMD_TIMER:
                    case CMD_TIMEOUT:
                        log.warn(
                                "ExtensionThread {}: 收到不应由 Extension 直接处理的命令 {} (Type: {}), 已忽略。",
                                threadName, message.getId(), message.getType());
                        if (message instanceof Command command) {
                            // 这里需要通过 ExtensionContext 回发 CommandResult
                        }
                        break;
                    default:
                        log.warn("ExtensionThread {}: 收到未知消息类型 {} (ID: {}), 已忽略。",
                                threadName, message.getType(), message.getId());
                        if (message instanceof Command command) {
                            // 这里需要通过 ExtensionContext 回发 CommandResult
                        }
                        break;
                }
            } catch (Exception e) {
                log.error("ExtensionThread {}: Extension {} 处理消息 {} 时发生异常: {}",
                        threadName, targetExtensionId, message.getId(), e.getMessage(), e);
                if (message instanceof Command) {
                    // 这里需要通过 ExtensionContext 回发 CommandResult
                }
            }
        });
    }

    /**
     * 将命令分发给此 ExtensionThread 上的目标 Extension。
     * 确保此方法可以从任何线程调用，并通过 Runloop 异步调度实际分发。
     * 注意：C++ 端通常将所有入站消息都视为 `msg`，这里为了类型安全单独列出 `Command`。
     */
    public void dispatchCommand(Command command, String targetExtensionId) {
        dispatchMessage(command, targetExtensionId); // 命令也是一种消息，直接委托给 dispatchMessage 处理
    }

    public ExtensionInfo getExtensionInfo(String extensionId) {
        return extensionInfos.get(extensionId);
    }

    public Extension getExtension(String extensionId) {
        return extensions.get(extensionId);
    }

    // 辅助方法，用于在需要时从 ExtensionThread 获取 ExtensionEnvImpl
    public ExtensionEnvImpl getExtensionEnv(String extensionId) {
        return extensionEnvs.get(extensionId);
    }
}