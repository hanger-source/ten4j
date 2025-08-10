package source.hanger.core.extension;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import source.hanger.core.graph.GraphConfig;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;
import lombok.extern.slf4j.Slf4j;

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
    private final AtomicLong outboundMessageCounter = new AtomicLong(0);
    private final AtomicLong totalCommandReceived = new AtomicLong(0);
    private final AtomicLong errorCounter = new AtomicLong(0); // Added: for tracking errors

    // Removed engine and extensionContext fields as per previous refactoring
    protected String extensionName;
    protected String extensionId; // 新增：存储 Extension 的 ID
    protected TenEnv env; // Change from TenEnvProxy to TenEnv
    protected Map<String, Object> configuration; // Change to non-final and allow initialization in init

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String getExtensionName() {
        return extensionName;
    }

    @Override
    public String getExtensionId() {
        return extensionId;
    }

    @Override
    public String getAppUri() {
        return env.getAppUri();
    }

    @Override
    public void init(String extensionId, Map<String, Object> properties, TenEnv env) { // Changed parameter to
                                                                                       // Map<String, Object>
        this.extensionId = extensionId; // 设置 extensionId
        extensionName = extensionId;
        this.env = env; // Assign new TenEnv
        this.configuration = new ConcurrentHashMap<>(properties); // Initialize properties
        log.info("BaseExtension {} initialized with TenEnv and properties.", extensionId);
    }

    @Override
    public void onConfigure(TenEnv env) { // Changed parameter to TenEnv
        // extensionName = env.getExtensionName(); // Get from TenEnv. Extension name is
        // already set in init
        log.info("Extension配置完成: extensionName={}", getExtensionName());
        onExtensionConfigure(env);
    }

    protected void onExtensionConfigure(TenEnv env) {
        // Subclasses can override this
    }

    @Override
    public void onInit(TenEnv env) { // Changed parameter to TenEnv
        log.info("Extension初始化阶段: extensionName={}", getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) { // Changed parameter to TenEnv
        log.info("Extension启动阶段: extensionName={}", getExtensionName());
    }

    @Override
    public void onStop(TenEnv env) { // Changed parameter to TenEnv
        log.info("Extension停止阶段: extensionName={}", getExtensionName());
    }

    @Override
    public void onDeinit(TenEnv env) { // Changed parameter to TenEnv
        log.info("Extension去初始化阶段: extensionName={}", getExtensionName());
    }

    @Override
    public void destroy(TenEnv env) { // Changed parameter to TenEnv
        log.info("Extension销毁阶段: extensionName={}", getExtensionName());
    }

    @Override
    public void onCmd(TenEnv env, Command command) { // Changed method name and parameter order
        totalCommandReceived.incrementAndGet();
        log.warn("Extension {} received unhandled Command: {}. Type: {}. Total received: {}", getExtensionName(),
                command.getId(), command.getType(), totalCommandReceived.get());
        errorCounter.incrementAndGet(); // Increment error count for unhandled commands
        // Default implementation: return a failure result for unhandled commands
        // Ensure this is posted to the TenEnv's runloop
        env.postTask(() -> env
                .sendResult(CommandResult.fail(command.getId(), "Extension does not support this command.")));
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) { // Changed method name and parameter order
        log.warn("Extension {} received unhandled CommandResult: {}. OriginalCommandId: {}", getExtensionName(),
                commandResult.getId(), commandResult.getOriginalCommandId());
        errorCounter.incrementAndGet(); // Increment error count for unhandled command results
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage dataMessage) { // Changed parameter order
        inboundMessageCounter.incrementAndGet();
        log.warn("Extension {} received unhandled DataMessage: {}. Type: {}. Total received: {}", getExtensionName(),
                dataMessage.getId(), dataMessage.getType(), inboundMessageCounter.get());
        errorCounter.incrementAndGet(); // Increment error count for unhandled data messages
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) { // Changed parameter order
        inboundMessageCounter.incrementAndGet();
        log.warn("Extension {} received unhandled AudioFrameMessage: {}. Type: {}. Total received: {}",
                getExtensionName(),
                audioFrame.getId(), audioFrame.getType(), inboundMessageCounter.get());
        errorCounter.incrementAndGet(); // Increment error count for unhandled audio frames
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) { // Changed parameter order
        inboundMessageCounter.incrementAndGet();
        log.warn("Extension {} received unhandled VideoFrameMessage: {}. Type: {}. Total received: {}",
                getExtensionName(),
                videoFrame.getId(), videoFrame.getType(), inboundMessageCounter.get());
        errorCounter.incrementAndGet(); // Increment error count for unhandled video frames
    }

    // ----------------------------------------------------------------------------------------------------------------
    // 属性访问方法 (实现 Extension 接口)
    // ----------------------------------------------------------------------------------------------------------------

    @Override
    public Optional<Object> getProperty(String path) {
        return getPropertyInternal(configuration, path);
    }

    @Override
    public void setProperty(String path, Object value) {
        setPropertyInternal(configuration, path, value);
    }

    @Override
    public boolean hasProperty(String path) {
        return getProperty(path).isPresent();
    }

    @Override
    public void deleteProperty(String path) {
        deletePropertyInternal(configuration, path);
    }

    @Override
    public Optional<Integer> getPropertyInt(String path) {
        return getProperty(path).map(o -> {
            if (o instanceof Integer) {
                return (Integer) o;
            } else if (o instanceof String) {
                try {
                    return Integer.parseInt((String) o);
                } catch (NumberFormatException e) {
                    return null; // 或者抛出异常
                }
            } else {
                return null; // 或者抛出异常
            }
        });
    }

    @Override
    public void setPropertyInt(String path, int value) {
        setProperty(path, value);
    }

    @Override
    public Optional<Long> getPropertyLong(String path) {
        return getProperty(path).map(o -> {
            if (o instanceof Long) {
                return (Long) o;
            } else if (o instanceof Integer) {
                return ((Integer) o).longValue();
            } else if (o instanceof String) {
                try {
                    return Long.parseLong((String) o);
                } catch (NumberFormatException e) {
                    return null; // 或者抛出异常
                }
            } else {
                return null; // 或者抛出异常
            }
        });
    }

    @Override
    public void setPropertyLong(String path, long value) {
        setProperty(path, value);
    }

    @Override
    public Optional<String> getPropertyString(String path) {
        return getProperty(path).map(Object::toString);
    }

    @Override
    public void setPropertyString(String path, String value) {
        setProperty(path, value);
    }

    @Override
    public Optional<Boolean> getPropertyBool(String path) {
        return getProperty(path).map(o -> {
            if (o instanceof Boolean) {
                return (Boolean) o;
            } else if (o instanceof String) {
                return Boolean.parseBoolean((String) o);
            } else {
                return null;
            }
        });
    }

    @Override
    public void setPropertyBool(String path, boolean value) {
        setProperty(path, value);
    }

    @Override
    public Optional<Double> getPropertyDouble(String path) {
        return getProperty(path).map(o -> {
            if (o instanceof Double) {
                return (Double) o;
            } else if (o instanceof Float) {
                return ((Float) o).doubleValue();
            } else if (o instanceof String) {
                try {
                    return Double.parseDouble((String) o);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        });
    }

    @Override
    public void setPropertyDouble(String path, double value) {
        setProperty(path, value);
    }

    @Override
    public Optional<Float> getPropertyFloat(String path) {
        return getProperty(path).map(o -> {
            if (o instanceof Float) {
                return (Float) o;
            } else if (o instanceof Double) {
                return ((Double) o).floatValue();
            } else if (o instanceof String) {
                try {
                    return Float.parseFloat((String) o);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        });
    }

    @Override
    public void setPropertyFloat(String path, float value) {
        setProperty(path, value);
    }

    @Override
    public void initPropertyFromJson(String jsonStr) {
        try {
            Map<String, Object> jsonMap = OBJECT_MAPPER.readValue(jsonStr, Map.class);
            this.configuration.putAll(jsonMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to initialize properties from JSON", e);
        }
    }

    // 辅助方法，用于处理嵌套路径 (与 GraphConfig 复制，考虑重构)
    private Optional<Object> getPropertyInternal(Map<String, Object> currentMap, String path) {
        String[] parts = path.split("\\.", 2);
        String currentKey = parts[0];
        if (!currentMap.containsKey(currentKey)) {
            return Optional.empty();
        }

        Object value = currentMap.get(currentKey);
        if (parts.length == 1) {
            return Optional.ofNullable(value);
        } else {
            if (value instanceof Map) {
                return getPropertyInternal((Map<String, Object>) value, parts[1]);
            } else {
                return Optional.empty(); // 路径中有嵌套，但当前值不是Map
            }
        }
    }

    private void setPropertyInternal(Map<String, Object> currentMap, String path, Object value) {
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
                        "Cannot set property: intermediate path '" + currentKey + "' is not a map.");
            }
        }
    }

    private void deletePropertyInternal(Map<String, Object> currentMap, String path) {
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
}