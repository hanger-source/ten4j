package source.hanger.core.engine;

import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.app.App;
import source.hanger.core.extension.Extension;
import source.hanger.core.graph.GraphDefinition;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.runloop.Runloop;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.message.CommandExecutionHandle;

/**
 * EngineEnvImpl 是 Engine 级别的 TenEnv 实现。
 * 它为 Extension 提供与 Engine 交互的接口。
 */
@Slf4j
public class EngineEnvImpl implements TenEnv {

    @Getter
    private final Engine engine;
    private final Runloop engineRunloop;
    private final App app;

    public EngineEnvImpl(Engine engine, Runloop engineRunloop, GraphDefinition graphDefinition, App app) {
        this.engine = engine;
        this.engineRunloop = engineRunloop;
        this.app = app;
        log.info("EngineEnvImpl created for Engine: {}", engine.getGraphId());
    }

    @Override
    public Runloop getRunloop() {
        return engineRunloop;
    }

    @Override
    public void postTask(Runnable task) {
        engineRunloop.postTask(task);
    }

    @Override
    public CommandExecutionHandle<CommandResult> sendAsyncCmd(Command command) {
        return engine.submitCommand(command);
    }

    @Override
    public void sendResult(CommandResult result) {
        engine.submitCommandResult(result);
    }

    @Override
    public void sendData(DataMessage data) {
        engine.submitInboundMessage(data, null); // Engine 接收消息
    }

    @Override
    public void sendVideoFrame(VideoFrameMessage videoFrame) {
        engine.submitInboundMessage(videoFrame, null); // Engine 接收消息
    }

    @Override
    public void sendAudioFrame(AudioFrameMessage audioFrame) {
        engine.submitInboundMessage(audioFrame, null); // Engine 接收消息
    }

    @Override
    public void sendMessage(Message message) {
        engine.submitInboundMessage(message, null); // Engine 接收消息
    }

    @Override
    public Optional<Object> getProperty(String path) {
        // EngineEnvImpl 不直接支持通用属性访问，应通过 GraphDefinition 的明确字段获取
        log.warn("EngineEnvImpl: 通用属性访问已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setProperty(String path, Object value) {
        throw new UnsupportedOperationException("EngineEnvImpl: 通用属性设置已不再支持。");
    }

    @Override
    public boolean hasProperty(String path) {
        log.warn("EngineEnvImpl: 通用属性检查已不再支持。请求路径: {}", path);
        return false;
    }

    @Override
    public void deleteProperty(String path) {
        throw new UnsupportedOperationException("EngineEnvImpl: 通用属性删除已不再支持。");
    }

    @Override
    public Optional<Integer> getPropertyInt(String path) {
        log.warn("EngineEnvImpl: 通用属性访问 (int) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyInt(String path, int value) {
        throw new UnsupportedOperationException("EngineEnvImpl: 通用属性设置 (int) 已不再支持。");
    }

    @Override
    public Optional<Long> getPropertyLong(String path) {
        log.warn("EngineEnvImpl: 通用属性访问 (long) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyLong(String path, long value) {
        throw new UnsupportedOperationException("EngineEnvImpl: 通用属性设置 (long) 已不再支持。");
    }

    @Override
    public Optional<String> getPropertyString(String path) {
        log.warn("EngineEnvImpl: 通用属性访问 (string) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyString(String path, String value) {
        throw new UnsupportedOperationException("EngineEnvImpl: 通用属性设置 (string) 已不再支持。");
    }

    @Override
    public Optional<Boolean> getPropertyBool(String path) {
        log.warn("EngineEnvImpl: 通用属性访问 (boolean) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyBool(String path, boolean value) {
        throw new UnsupportedOperationException("EngineEnvImpl: 通用属性设置 (boolean) 已不再支持。");
    }

    @Override
    public Optional<Double> getPropertyDouble(String path) {
        log.warn("EngineEnvImpl: 通用属性访问 (double) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyDouble(String path, double value) {
        throw new UnsupportedOperationException("EngineEnvImpl: 通用属性设置 (double) 已不再支持。");
    }

    @Override
    public Optional<Float> getPropertyFloat(String path) {
        log.warn("EngineEnvImpl: 通用属性访问 (float) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyFloat(String path, float value) {
        throw new UnsupportedOperationException("EngineEnvImpl: 通用属性设置 (float) 已不再支持。");
    }

    @Override
    public void initPropertyFromJson(String jsonStr) {
        throw new UnsupportedOperationException("EngineEnvImpl: 从 JSON 初始化属性已不再支持。");
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
        return null; // EngineEnvImpl 不直接表示某个 Extension
    }

    @Override
    public Extension getAttachedExtension() {
        return null; // EngineEnvImpl 不会附加到 Extension
    }

    @Override
    public void close() {
        // EngineEnvImpl 的 close 行为由 Engine 负责管理
    }
}