package source.hanger.core.engine;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import source.hanger.core.app.App;
import source.hanger.core.extension.Extension;
import source.hanger.core.graph.GraphConfig;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.runloop.Runloop;
import source.hanger.core.tenenv.TenEnv;
import lombok.extern.slf4j.Slf4j;

/**
 * EngineEnvImpl 是 Engine 级别的 TenEnv 实现。
 * 它为 Extension 提供与 Engine 交互的接口。
 */
@Slf4j
public class EngineEnvImpl implements TenEnv {

    private final Engine engine;
    private final Runloop engineRunloop;
    private final Map<String, Object> properties; // Engine 的配置属性，来自 GraphDefinition
    private final App app;

    public EngineEnvImpl(Engine engine, Runloop engineRunloop, Map<String, Object> properties, App app) {
        this.engine = engine;
        this.engineRunloop = engineRunloop;
        this.properties = properties;
        this.app = app; // Added
        log.info("EngineEnvImpl created for Engine: {}", engine.getGraphId());
    }

    public Engine getEngine() {
        return engine;
    }

    @Override
    public void postTask(Runnable task) {
        engineRunloop.postTask(task);
    }

    @Override
    public CompletableFuture<CommandResult> sendCmd(Command command) {
        return engine.submitCommand(command);
    }

    @Override
    public void sendResult(CommandResult result) {
        engine.submitCommandResult(result);
    }

    @Override
    public void sendData(DataMessage data) {
        engine.submitInboundMessage(data, null);
    }

    @Override
    public void sendVideoFrame(VideoFrameMessage videoFrame) {
        engine.submitInboundMessage(videoFrame, null);
    }

    @Override
    public void sendAudioFrame(AudioFrameMessage audioFrame) {
        engine.submitInboundMessage(audioFrame, null);
    }

    @Override
    public void sendMessage(Message message) {
        engine.submitInboundMessage(message, null);
    }

    @Override
    public Optional<Object> getProperty(String path) {
        return Optional.ofNullable(properties.get(path)); // <-- 使用 properties Map
    }

    @Override
    public void setProperty(String path, Object value) {
        properties.put(path, value); // <-- 使用 properties Map
    }

    @Override
    public boolean hasProperty(String path) {
        return properties.containsKey(path); // <-- 使用 properties Map
    }

    @Override
    public void deleteProperty(String path) {
        properties.remove(path); // <-- 使用 properties Map
    }

    @Override
    public Optional<Integer> getPropertyInt(String path) {
        Object value = properties.get(path); // <-- 使用 properties Map
        if (value instanceof Integer) {
            return Optional.of((Integer) value);
        }
        return Optional.empty();
    }

    @Override
    public void setPropertyInt(String path, int value) {
        properties.put(path, value); // <-- 使用 properties Map
    }

    @Override
    public Optional<Long> getPropertyLong(String path) {
        Object value = properties.get(path); // <-- 使用 properties Map
        if (value instanceof Long) {
            return Optional.of((Long) value);
        }
        return Optional.empty();
    }

    @Override
    public void setPropertyLong(String path, long value) {
        properties.put(path, value); // <-- 使用 properties Map
    }

    @Override
    public Optional<String> getPropertyString(String path) {
        Object value = properties.get(path); // <-- 使用 properties Map
        if (value instanceof String) {
            return Optional.of((String) value);
        }
        return Optional.empty();
    }

    @Override
    public void setPropertyString(String path, String value) {
        properties.put(path, value); // <-- 使用 properties Map
    }

    @Override
    public Optional<Boolean> getPropertyBool(String path) {
        Object value = properties.get(path); // <-- 使用 properties Map
        if (value instanceof Boolean) {
            return Optional.of((Boolean) value);
        }
        return Optional.empty();
    }

    @Override
    public void setPropertyBool(String path, boolean value) {
        properties.put(path, value); // <-- 使用 properties Map
    }

    @Override
    public Optional<Double> getPropertyDouble(String path) {
        Object value = properties.get(path); // <-- 使用 properties Map
        if (value instanceof Double) {
            return Optional.of((Double) value);
        }
        return Optional.empty();
    }

    @Override
    public void setPropertyDouble(String path, double value) {
        properties.put(path, value); // <-- 使用 properties Map
    }

    @Override
    public Optional<Float> getPropertyFloat(String path) {
        Object value = properties.get(path); // <-- 使用 properties Map
        if (value instanceof Float) {
            return Optional.of((Float) value);
        }
        return Optional.empty();
    }

    @Override
    public void setPropertyFloat(String path, float value) {
        properties.put(path, value); // <-- 使用 properties Map
    }

    @Override
    public void initPropertyFromJson(String jsonStr) {
        // 对于 Map<String, Object> 类型的 properties，需要手动解析 JSON
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> newProperties = new ObjectMapper().readValue(jsonStr, Map.class);
            this.properties.clear();
            this.properties.putAll(newProperties);
            log.debug("EngineEnvImpl: 从 JSON 初始化属性成功。");
        } catch (IOException e) {
            log.error("EngineEnvImpl: 从 JSON 初始化属性失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getAppUri() {
        return app.getAppUri();
    }

    @Override
    public String getGraphId() {
        return engine.getGraphId();
    }

    @Override
    public String getExtensionName() {
        return null; // EngineEnvImpl 不直接代表 Extension
    }

    @Override
    public Extension getAttachedExtension() {
        return null; // EngineEnvImpl 不会附加到 Extension
    }

    @Override
    public void close() {
        // EngineEnvImpl 的 close 行为取决于 Engine 的停止，无需额外操作
        log.info("EngineEnvImpl {} closed.", engine.getGraphId());
    }
}