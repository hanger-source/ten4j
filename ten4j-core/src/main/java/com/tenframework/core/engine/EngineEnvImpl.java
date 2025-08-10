package com.tenframework.core.engine;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.tenframework.core.app.App;
import com.tenframework.core.extension.Extension;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.runloop.Runloop;
import com.tenframework.core.tenenv.TenEnv;
import lombok.extern.slf4j.Slf4j;

/**
 * `EngineEnvImpl` 是 `Engine` 组件的 `TenEnv` 接口实现。
 * 它将 `TenEnv` 的操作委托给其持有的 `Engine` 实例。
 */
@Slf4j
public class EngineEnvImpl implements TenEnv {

    private final Engine engine;
    private final Runloop engineRunloop;
    private final GraphConfig graphConfig;
    private final App app; // 用于获取 App URI

    public EngineEnvImpl(Engine engine, Runloop engineRunloop, GraphConfig graphConfig, App app) {
        this.engine = engine;
        this.engineRunloop = engineRunloop;
        this.graphConfig = graphConfig;
        this.app = app;
        log.info("EngineEnvImpl created for Engine: {}", engine.getGraphId());
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
        engine.submitCommandResult(result); // Changed to submitCommandResult
    }

    @Override
    public void sendData(DataMessage data) {
        engine.submitMessage(data);
    }

    @Override
    public void sendVideoFrame(VideoFrameMessage videoFrame) {
        engine.submitMessage(videoFrame);
    }

    @Override
    public void sendAudioFrame(AudioFrameMessage audioFrame) {
        engine.submitMessage(audioFrame);
    }

    @Override
    public void sendMessage(com.tenframework.core.message.Message message) {
        engine.submitMessage(message);
    }

    @Override
    public Optional<Object> getProperty(String path) {
        return graphConfig.getProperty(path);
    }

    @Override
    public void setProperty(String path, Object value) {
        graphConfig.setProperty(path, value);
    }

    @Override
    public boolean hasProperty(String path) {
        return graphConfig.hasProperty(path);
    }

    @Override
    public void deleteProperty(String path) {
        graphConfig.deleteProperty(path);
    }

    @Override
    public Optional<Integer> getPropertyInt(String path) {
        return graphConfig.getPropertyInt(path);
    }

    @Override
    public void setPropertyInt(String path, int value) {
        graphConfig.setPropertyInt(path, value);
    }

    @Override
    public Optional<Long> getPropertyLong(String path) {
        return graphConfig.getPropertyLong(path);
    }

    @Override
    public void setPropertyLong(String path, long value) {
        graphConfig.setPropertyLong(path, value);
    }

    @Override
    public Optional<String> getPropertyString(String path) {
        return graphConfig.getPropertyString(path);
    }

    @Override
    public void setPropertyString(String path, String value) {
        graphConfig.setPropertyString(path, value);
    }

    @Override
    public Optional<Boolean> getPropertyBool(String path) {
        return graphConfig.getPropertyBool(path);
    }

    @Override
    public void setPropertyBool(String path, boolean value) {
        graphConfig.setPropertyBool(path, value);
    }

    @Override
    public Optional<Double> getPropertyDouble(String path) {
        return graphConfig.getPropertyDouble(path);
    }

    @Override
    public void setPropertyDouble(String path, double value) {
        graphConfig.setPropertyDouble(path, value);
    }

    @Override
    public Optional<Float> getPropertyFloat(String path) {
        return graphConfig.getPropertyFloat(path);
    }

    @Override
    public void setPropertyFloat(String path, float value) {
        graphConfig.setPropertyFloat(path, value);
    }

    @Override
    public void initPropertyFromJson(String jsonStr) {
        graphConfig.initPropertyFromJson(jsonStr);
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
        return null; // Engine 不是 Extension
    }

    @Override
    public Extension getAttachedExtension() {
        return null; // Engine 不会附加到 Extension
    }

    @Override
    public void close() {
        engine.stop(); // 调用 Engine 的停止方法
    }
}