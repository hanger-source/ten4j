package source.hanger.core.extension.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.Extension;
import source.hanger.core.extension.component.state.DefaultExtensionStateProvider;
import source.hanger.core.extension.component.state.ExtensionStateProvider;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

/**
 * 基础Extension抽象类
 * 提供丰富的底层能力，让开发者开箱即用
 *
 * 核心能力：
 * 1. 自动生命周期管理
 * 2. 内置消息队列和异步处理
 * 3. 自动错误处理和重试机制
 * 4. 内置性能监控和健康检查
 * 5. 自动资源管理和清理
 * 6. 内置配置管理和热更新
 * 7. 自动日志记录和调试支持
 */
@Slf4j
public abstract class BaseExtension implements Extension {

    private final AtomicLong inboundMessageCounter = new AtomicLong(0);
    private final AtomicLong totalCommandReceived = new AtomicLong(0);
    private final AtomicLong errorCounter = new AtomicLong(0);
    protected ExtensionStateProvider extensionStateProvider; // 统一的状态管理组件
    protected TenEnv env;
    protected Map<String, Object> configuration;

    @Override
    public final void onConfigure(TenEnv env, Map<String, Object> properties) {
        this.env = env; // 确保 env 已设置
        this.configuration = new ConcurrentHashMap<>(properties);
        // 初始化状态提供者
        this.extensionStateProvider = createExtensionStateProvider(env.getExtensionName());
        log.info("[{}] Extension配置完成", env.getExtensionName());
        onExtensionConfigure(env, properties);
    }

    protected void onExtensionConfigure(TenEnv env,  Map<String, Object> properties) {
        // Subclasses can override this
    }

    @Override
    public void onInit(TenEnv env) {
        log.info("[{}] Extension初始化阶段", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        log.info("[{}] Extension启动阶段", env.getExtensionName());
        if (extensionStateProvider != null) {
            extensionStateProvider.start();
        }
    }

    @Override
    public void onStop(TenEnv env) {
        log.info("[{}] Extension停止阶段", env.getExtensionName());
        if (extensionStateProvider != null) {
            extensionStateProvider.stop();
        }
    }

    @Override
    public void onDeinit(TenEnv env) {
        log.info("[{}] Extension去初始化阶段", env.getExtensionName());
        if (extensionStateProvider != null) {
            extensionStateProvider.reset();
        }
    }

    @Override
    public void onDestroy(TenEnv env) {
        log.info("[{}] Extension销毁阶段", env.getExtensionName());
    }

    @Override
    public void onCmd(TenEnv env, Command command) { // Changed method name and parameter order
        totalCommandReceived.incrementAndGet();
        log.warn("[{}] Extension received unhandled Command: {}. Type: {}. Total received: {}", env.getExtensionName(),
                command.getId(), command.getType(), totalCommandReceived.get());
        errorCounter.incrementAndGet();
        env.postTask(() -> env
                .sendResult(CommandResult.fail(command, "Extension does not support this command.")));
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) { // Changed method name and parameter order
        log.warn("[{}] Extension received unhandled CommandResult: {}. OriginalCommandId: {}", env.getExtensionName(),
                commandResult.getId(), commandResult.getOriginalCommandId());
        errorCounter.incrementAndGet(); // Increment error count for unhandled command results
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage dataMessage) { // Changed parameter order
        inboundMessageCounter.incrementAndGet();
        log.warn("[{}] Extension received unhandled DataMessage: {}. Type: {}. Total received: {}",
                env.getExtensionName(),
                dataMessage.getId(), dataMessage.getType(), inboundMessageCounter.get());
        errorCounter.incrementAndGet(); // Increment error count for unhandled data messages
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) { // Changed parameter order
        inboundMessageCounter.incrementAndGet();
        log.warn("[{}] Extension received unhandled AudioFrameMessage: {}. Type: {}. Total received: {}",
                env.getExtensionName(),
                audioFrame.getId(), audioFrame.getType(), inboundMessageCounter.get());
        errorCounter.incrementAndGet(); // Increment error count for unhandled audio frames
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) { // Changed parameter order
        inboundMessageCounter.incrementAndGet();
        log.warn("[{}] Extension received unhandled VideoFrameMessage: {}. Type: {}. Total received: {}",
                env.getExtensionName(),
                videoFrame.getId(), videoFrame.getType(), inboundMessageCounter.get());
        errorCounter.incrementAndGet(); // Increment error count for unhandled video frames
    }

    @Override
    public String getExtensionName() {
        if (env != null) {
            return env.getExtensionName();
        }
        return "UnknownExtension"; // 或者抛出异常，取决于设计
    }

    // Getter for isRunning
    public boolean isRunning() {
        return extensionStateProvider != null && extensionStateProvider.isRunning();
    }

    /**
     * 创建 Extension 状态提供者实例。
     * 子类可以重写此方法以提供自定义的状态管理实现。
     *
     * @param extensionName Extension 名称。
     * @return Extension 状态提供者实例。
     */
    protected ExtensionStateProvider createExtensionStateProvider(String extensionName) {
        return new DefaultExtensionStateProvider(extensionName);
    }
}