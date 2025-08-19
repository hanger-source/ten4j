package source.hanger.core.app;

import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
import source.hanger.core.tenenv.RunloopFuture;

/**
 * AppEnvImpl 是 App 级别的 TenEnv 实现。
 * 它为 Extension 提供与 App 交互的接口。
 */
@Slf4j
public class AppEnvImpl implements TenEnv {

    @Getter
    private final App app;
    private final Runloop appRunloop;
    // AppConfig 现在通过其明确的字段进行访问，不再支持通用的 property 访问
    private final GraphConfig appConfig;

    public AppEnvImpl(App app, Runloop appRunloop, GraphConfig appConfig) {
        this.app = app;
        this.appRunloop = appRunloop;
        this.appConfig = appConfig;
        log.info("AppEnvImpl created for App: {}", app.getAppUri());
    }

    @Override
    public Runloop getRunloop() {
        return appRunloop;
    }

    @Override
    public void postTask(Runnable task) {
        appRunloop.postTask(task);
    }

    @Override
    public RunloopFuture<CommandResult> sendAsyncCmd(Command command) {
        return app.submitCommand(command);
    }

    @Override
    public void sendResult(CommandResult result) {
        app.handleInboundMessage(result, null);
    }

    @Override
    public void sendData(DataMessage data) {
        app.handleInboundMessage(data, null);
    }

    @Override
    public void sendVideoFrame(VideoFrameMessage videoFrame) {
        app.handleInboundMessage(videoFrame, null);
    }

    @Override
    public void sendAudioFrame(AudioFrameMessage audioFrame) {
        app.handleInboundMessage(audioFrame, null);
    }

    @Override
    public void sendMessage(Message message) {
        app.handleInboundMessage(message, null);
    }

    @Override
    public Optional<Object> getProperty(String path) {
        log.warn("AppEnvImpl: 通用属性访问已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setProperty(String path, Object value) {
        throw new UnsupportedOperationException("AppEnvImpl: 通用属性设置已不再支持。");
    }

    @Override
    public boolean hasProperty(String path) {
        log.warn("AppEnvImpl: 通用属性检查已不再支持。请求路径: {}", path);
        return false;
    }

    @Override
    public void deleteProperty(String path) {
        throw new UnsupportedOperationException("AppEnvImpl: 通用属性删除已不再支持。");
    }

    @Override
    public Optional<Integer> getPropertyInt(String path) {
        log.warn("AppEnvImpl: 通用属性访问 (int) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyInt(String path, int value) {
        throw new UnsupportedOperationException("AppEnvImpl: 通用属性设置 (int) 已不再支持。");
    }

    @Override
    public Optional<Long> getPropertyLong(String path) {
        log.warn("AppEnvImpl: 通用属性访问 (long) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyLong(String path, long value) {
        throw new UnsupportedOperationException("AppEnvImpl: 通用属性设置 (long) 已不再支持。");
    }

    @Override
    public Optional<String> getPropertyString(String path) {
        log.warn("AppEnvImpl: 通用属性访问 (string) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyString(String path, String value) {
        throw new UnsupportedOperationException("AppEnvImpl: 通用属性设置 (string) 已不再支持。");
    }

    @Override
    public Optional<Boolean> getPropertyBool(String path) {
        log.warn("AppEnvImpl: 通用属性访问 (boolean) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyBool(String path, boolean value) {
        throw new UnsupportedOperationException("AppEnvImpl: 通用属性设置 (boolean) 已不再支持。");
    }

    @Override
    public Optional<Double> getPropertyDouble(String path) {
        log.warn("AppEnvImpl: 通用属性访问 (double) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyDouble(String path, double value) {
        throw new UnsupportedOperationException("AppEnvImpl: 通用属性设置 (double) 已不再支持。");
    }

    @Override
    public Optional<Float> getPropertyFloat(String path) {
        log.warn("AppEnvImpl: 通用属性访问 (float) 已不再支持。请求路径: {}", path);
        return Optional.empty();
    }

    @Override
    public void setPropertyFloat(String path, float value) {
        throw new UnsupportedOperationException("AppEnvImpl: 通用属性设置 (float) 已不再支持。");
    }

    @Override
    public void initPropertyFromJson(String jsonStr) {
        throw new UnsupportedOperationException("AppEnvImpl: 从 JSON 初始化属性已不再支持。");
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