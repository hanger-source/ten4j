package source.hanger.core.extension;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import source.hanger.core.engine.EngineExtensionContext;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.runloop.Runloop;

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
    private final EngineExtensionContext engineExtensionContext;
    @Setter
    @Getter // 新增：为 extensionGroup 添加 Getter 注解
    private ExtensionGroup extensionGroup; // 新增：ExtensionGroup 实例

    public ExtensionThread(String threadName, EngineExtensionContext engineExtensionContext) {
        this.threadName = threadName;
        this.engineExtensionContext = engineExtensionContext;
        runloop = Runloop.createRunloop("%s-Runloop".formatted(threadName));
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
                extensionGroup.getExtensionGroupInfo() // 传入 ExtensionGroupInfo
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
            ExtensionEnvImpl extensionGroupEnv = (ExtensionEnvImpl)extensionGroup
                .getTenEnv();
            runloop.postTask(() -> {
                try {
                    extensionGroup.onDeinit(extensionGroupEnv);
                    log.info("ExtensionGroup {}: onDeinit completed. ", extensionGroup.getName());
                } catch (Exception e) {
                    log.error("ExtensionGroup {}: Error during onDeinit: {}",
                        extensionGroup.getName(), e.getMessage(), e);
                }
            });
        }
        log.info("ExtensionThread {} closed.", threadName);
    }

    /**
     * 将 Extension 添加到此线程的管理列表，并执行其生命周期回调。
     * 确保此方法在 ExtensionThread 的 Runloop 线程上被调用。
     * 此方法将直接委托给 ExtensionGroup 处理 Extension 的添加和生命周期。
     */
    public void addExtension(Extension extension, ExtensionEnvImpl extensionEnv, ExtensionInfo extInfo) {
        runloop.postTask(() -> {
            if (extensionGroup != null) {
                extensionGroup.addManagedExtension(extensionEnv.getExtensionName(), extension, extensionEnv, extInfo);
                extensionGroup.onCreateExtensions(extensionGroup.getTenEnv());
                log.info("ExtensionThread {}: Extension {} added to group {} and onCreateExtensions called.",
                    threadName, extensionEnv.getExtensionName(), extensionGroup.getName());
            } else {
                log.error("ExtensionThread {}: Cannot add Extension {} because ExtensionGroup is null.",
                    threadName, extensionEnv.getExtensionName());
                // 这里应该抛出异常或更严格地处理，因为 Extension 必须属于一个 Group
            }
        });
    }

    /**
     * 从此线程的管理列表移除 Extension，并执行其生命周期回调。
     * 确保此方法在 ExtensionThread 的 Runloop 线程上被调用。
     */
    public void removeExtension(String extensionName) {
        runloop.postTask(() -> {
            if (extensionGroup != null) {
                extensionGroup.removeExtension(extensionName);
                log.info("ExtensionThread {}: Extension {} removed from group {}.",
                    threadName, extensionName, extensionGroup.getName());
            } else {
                log.error("ExtensionThread {}: Cannot remove Extension {} because ExtensionGroup is null.",
                    threadName, extensionName);
            }
        });
    }

    /**
     * 将消息分发给此 ExtensionThread 上的目标 Extension。
     * 确保此方法可以从任何线程调用，并通过 Runloop 异步调度实际分发。
     */
    public void dispatchMessage(Message message, String targetExtensionName) {
        runloop.postTask(() -> {
            if (extensionGroup == null) {
                log.error("ExtensionThread {}: ExtensionGroup 未设置，无法分发消息 {} 到 Extension {}.",
                    threadName, message.getId(), targetExtensionName);
                if (message instanceof Command command) {
                    engineExtensionContext.routeCommandResultFromExtension(CommandResult.fail(command,
                            "ExtensionGroup is null for ExtensionThread: %s".formatted(threadName)),
                        targetExtensionName);
                }
                return;
            }

            ExtensionEnvImpl extensionEnv = extensionGroup.getManagedExtensionEnv(targetExtensionName);
            Extension extension = (extensionEnv != null) ? extensionEnv.getExtension() : null;

            if (extension == null) {
                log.error(
                    "ExtensionThread {}: 无法找到 Extension {} 或其环境，无法分发消息 {}. 请检查 extensionName 和 extensionGroup 配置。",
                    threadName, targetExtensionName, message.getId());
                if (message instanceof Command command) {
                    engineExtensionContext.routeCommandResultFromExtension(CommandResult.fail(command,
                            "Extension or ExtensionEnv not found in group: %s for message %s"
                                .formatted(targetExtensionName, message.getId())),
                        targetExtensionName);
                }
                return;
            }

            try {
                switch (message.getType()) {
                    case CMD:
                        extension.onCmd(extensionEnv, (Command)message);
                        break;
                    case CMD_RESULT:
                        extension.onCmdResult(extensionEnv, (CommandResult)message);
                        break;
                    case DATA:
                        extension.onDataMessage(extensionEnv, (DataMessage)message);
                        break;
                    case AUDIO_FRAME:
                        extension.onAudioFrame(extensionEnv, (AudioFrameMessage)message);
                        break;
                    case VIDEO_FRAME:
                        extension.onVideoFrame(extensionEnv, (VideoFrameMessage)message);
                        break;
                    case CMD_CLOSE_APP:
                    case CMD_START_GRAPH:
                    case CMD_STOP_GRAPH:
                    case CMD_TIMER:
                    case CMD_TIMEOUT:
                        log.warn(
                            "ExtensionThread {}: 收到不应由 Extension {} 直接处理的命令 {} (Type: {}), 已忽略。该命令应在 Engine/App 级别处理。",
                            threadName, targetExtensionName, message.getId(), message.getType());
                        if (message instanceof Command command) {
                            engineExtensionContext.routeCommandResultFromExtension(CommandResult.fail(command,
                                    "App/Engine-level command not handled by Extension: %s"
                                        .formatted(command.getType())),
                                targetExtensionName);
                        }
                        break;
                    default:
                        log.warn("ExtensionThread {}: 收到 Extension {} 未知消息类型 {} (ID: {}), 已忽略。",
                            threadName, targetExtensionName, message.getType(), message.getId());
                        if (message instanceof Command command) {
                            engineExtensionContext.routeCommandResultFromExtension(CommandResult.fail(command,
                                    "Unknown message type not handled by Extension: %s".formatted(command.getType())),
                                targetExtensionName);
                        }
                        break;
                }
            } catch (Exception e) {
                log.error("ExtensionThread {}: Extension {} 处理消息 {} 时发生异常: {}",
                    threadName, targetExtensionName, message.getId(), e.getMessage(), e);
                if (message instanceof Command command) {
                    engineExtensionContext.routeCommandResultFromExtension(CommandResult.fail(command,
                            "Error processing message by Extension: %s".formatted(e.getMessage())),
                        targetExtensionName);
                }
            }
        });
    }

    /**
     * 将命令分发给此 ExtensionThread 上的目标 Extension。
     * 确保此方法可以从任何线程调用，并通过 Runloop 异步调度实际分发。
     */
    public void dispatchCommand(Command command, String targetExtensionId) {
        dispatchMessage(command, targetExtensionId); // 命令也是一种消息，直接委托给 dispatchMessage 处理
    }
}