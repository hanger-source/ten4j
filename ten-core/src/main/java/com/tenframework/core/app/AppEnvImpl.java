package com.tenframework.core.app;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
 * `AppEnvImpl` 是 `App` 组件的 `TenEnv` 接口实现。
 * 它将 `TenEnv` 的操作委托给其持有的 `App` 实例。
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
        app.submitInboundMessage(data, null); // App 接收的消息通常是入站消息
    }

    @Override
    public void sendVideoFrame(VideoFrameMessage videoFrame) {
        app.submitInboundMessage(videoFrame, null);
    }

    @Override
    public void sendAudioFrame(AudioFrameMessage audioFrame) {
        app.submitInboundMessage(audioFrame, null);
    }

    @Override
    public void sendMessage(com.tenframework.core.message.Message message) { // Implement sendMessage
        app.submitInboundMessage(message, null); // Delegate to app's submitInboundMessage
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