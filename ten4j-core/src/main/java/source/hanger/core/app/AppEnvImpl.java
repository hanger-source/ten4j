package source.hanger.core.app;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
 * AppEnvImpl 是 App 级别的 TenEnv 实现。
 * 它为 Extension 提供与 App 交互的接口。
 */
@Slf4j
public class AppEnvImpl implements TenEnv {

    private final App app;
    private final Runloop appRunloop;
    private final GraphConfig appConfig;

    public AppEnvImpl(App app, Runloop appRunloop, GraphConfig appConfig) {
        this.app = app;
        this.appRunloop = appRunloop;
        this.appConfig = appConfig;
        log.info("AppEnvImpl created for App: {}", app.getAppUri());
    }

    public App getApp() { // Added
        return app;
    }

    @Override
    public void postTask(Runnable task) {
        appRunloop.postTask(task);
    }

    @Override
    public CompletableFuture<CommandResult> sendCmd(Command command) {
        return app.submitCommand(command);
    }

    @Override
    public void sendResult(CommandResult result) {
        app.routeCommandResult(result);
    }

    @Override
    public void sendData(DataMessage data) {
        app.handleInboundMessage(data, null); // 修正：使用 handleInboundMessage
    }

    @Override
    public void sendVideoFrame(VideoFrameMessage videoFrame) {
        app.handleInboundMessage(videoFrame, null); // 修正：使用 handleInboundMessage
    }

    @Override
    public void sendAudioFrame(AudioFrameMessage audioFrame) {
        app.handleInboundMessage(audioFrame, null); // 修正：使用 handleInboundMessage
    }

    @Override
    public void sendMessage(Message message) {
        app.handleInboundMessage(message, null); // 修正：使用 handleInboundMessage
    }

    @Override
    public Optional<Object> getProperty(String path) {
        return appConfig.getProperty(path);
    }

    @Override
    public void setProperty(String path, Object value) {
        appConfig.setProperty(path, value);
    }

    @Override
    public boolean hasProperty(String path) {
        return appConfig.hasProperty(path);
    }

    @Override
    public void deleteProperty(String path) {
        appConfig.deleteProperty(path);
    }

    @Override
    public Optional<Integer> getPropertyInt(String path) {
        return appConfig.getPropertyInt(path);
    }

    @Override
    public void setPropertyInt(String path, int value) {
        appConfig.setPropertyInt(path, value);
    }

    @Override
    public Optional<Long> getPropertyLong(String path) {
        return appConfig.getPropertyLong(path);
    }

    @Override
    public void setPropertyLong(String path, long value) {
        appConfig.setPropertyLong(path, value);
    }

    @Override
    public Optional<String> getPropertyString(String path) {
        return appConfig.getPropertyString(path);
    }

    @Override
    public void setPropertyString(String path, String value) {
        appConfig.setPropertyString(path, value);
    }

    @Override
    public Optional<Boolean> getPropertyBool(String path) {
        return appConfig.getPropertyBool(path);
    }

    @Override
    public void setPropertyBool(String path, boolean value) {
        appConfig.setPropertyBool(path, value);
    }

    @Override
    public Optional<Double> getPropertyDouble(String path) {
        return appConfig.getPropertyDouble(path);
    }

    @Override
    public void setPropertyDouble(String path, double value) {
        appConfig.setPropertyDouble(path, value);
    }

    @Override
    public Optional<Float> getPropertyFloat(String path) {
        return appConfig.getPropertyFloat(path);
    }

    @Override
    public void setPropertyFloat(String path, float value) {
        appConfig.setPropertyFloat(path, value);
    }

    @Override
    public void initPropertyFromJson(String jsonStr) {
        appConfig.initPropertyFromJson(jsonStr);
    }

    @Override
    public String getAppUri() {
        return app.getAppUri();
    }

    @Override
    public String getGraphId() {
        return null; // App 不直接有 Graph ID 概念
    }

    @Override
    public String getExtensionName() {
        return null; // App 不是 Extension
    }

    @Override
    public Extension getAttachedExtension() {
        return null; // App 不会附加到 Extension
    }

    @Override
    public void close() {
        app.stop(); // 调用 App 的停止方法
    }
}