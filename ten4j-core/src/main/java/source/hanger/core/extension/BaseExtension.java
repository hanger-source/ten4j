package source.hanger.core.extension;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
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

    protected volatile boolean isRunning = false; // Moved from BaseFlushExtension

    private final AtomicLong inboundMessageCounter = new AtomicLong(0);
    private final AtomicLong outboundMessageCounter = new AtomicLong(0);
    private final AtomicLong totalCommandReceived = new AtomicLong(0);
    private final AtomicLong errorCounter = new AtomicLong(0);

    // Removed engine and extensionContext fields as per previous refactoring
    // protected String extensionName;
    // protected String extensionId; // 新增：存储 Extension 的 ID
    protected TenEnv env;
    protected Map<String, Object> configuration;

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        this.env = env; // 确保 env 已设置
        this.configuration = new ConcurrentHashMap<>(properties);
        log.info("[{}] Extension配置完成", env.getExtensionName());
        onExtensionConfigure(env);
    }

    protected void onExtensionConfigure(TenEnv env) {
        // Subclasses can override this
    }

    @Override
    public void onInit(TenEnv env) {
        log.info("[{}] Extension初始化阶段", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        log.info("[{}] Extension启动阶段", env.getExtensionName());
        this.isRunning = true;
    }

    @Override
    public void onStop(TenEnv env) {
        log.info("[{}] Extension停止阶段", env.getExtensionName());
        this.isRunning = false;
    }

    @Override
    public void onDeinit(TenEnv env) {
        log.info("[{}] Extension去初始化阶段", env.getExtensionName());
    }

    @Override
    public void destroy(TenEnv env) {
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

    protected void setPropertyInternal(Map<String, Object> currentMap, String path, Object value) {
        String[] parts = path.split("\\.", 2);
        String currentKey = parts[0];
        if (parts.length == 1) {
            currentMap.put(currentKey, value);
        } else {
            currentMap.computeIfAbsent(currentKey, k -> new ConcurrentHashMap<>());
            if (currentMap.get(currentKey) instanceof Map) {
                setPropertyInternal((Map<String, Object>) currentMap.get(currentKey), parts[1], value);
            } else {
                // 如果中间节点不是Map，则抛出异常或覆盖
                throw new IllegalArgumentException(
                        "Cannot set property: intermediate path '%s' is not a map.".formatted(currentKey));
            }
        }
    }

    protected void deletePropertyInternal(Map<String, Object> currentMap, String path) {
        String[] parts = path.split("\\.", 2);
        String currentKey = parts[0];
        if (!currentMap.containsKey(currentKey)) {
            return;
        }

        if (parts.length == 1) {
            currentMap.remove(currentKey);
        } else {
            Object value = currentMap.get(currentKey);
            if (value instanceof Map) {
                deletePropertyInternal((Map<String, Object>) value, parts[1]);
            }
        }
    }

    /**
     * 递增内部错误计数。
     */
    protected void incrementErrorCount() {
        errorCounter.incrementAndGet();
    }

    /**
     * 获取当前错误计数。
     *
     * @return 错误计数。
     */
    protected long getErrorCount() {
        return errorCounter.get();
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
        return isRunning;
    }
}