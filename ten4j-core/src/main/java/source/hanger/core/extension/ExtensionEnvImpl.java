package source.hanger.core.extension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import source.hanger.core.engine.EngineExtensionContext;
import source.hanger.core.extension.submitter.ExtensionCommandSubmitter;
import source.hanger.core.extension.submitter.ExtensionMessageSubmitter;
import source.hanger.core.graph.GraphConfig;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.runloop.Runloop;
import source.hanger.core.tenenv.TenEnv;
import lombok.extern.slf4j.Slf4j;

/**
 * `ExtensionEnvImpl` 是 `Extension` 组件的 `TenEnv` 接口实现。
 * 它将 `TenEnv` 的操作委托给其持有的 `Extension` 实例及其相关的提交器。
 * 这是一个 Runloop 线程安全的类，因为所有操作都通过 `postTask` 提交到 Runloop。
 */
@Slf4j
public class ExtensionEnvImpl implements TenEnv {

    private final String extensionId;
    private final Extension extension;
    private final String appUri;
    private final String graphId;
    private final ExtensionCommandSubmitter commandSubmitter;
    private final ExtensionMessageSubmitter messageSubmitter;
    private final Runloop extensionRunloop; // Extension 所在的 Runloop
    private final EngineExtensionContext extensionContext;

    public ExtensionEnvImpl(String extensionId, Extension extension, String appUri, String graphId,
            ExtensionCommandSubmitter commandSubmitter, ExtensionMessageSubmitter messageSubmitter,
            Runloop extensionRunloop, EngineExtensionContext extensionContext) {
        this.extensionId = extensionId;
        this.extension = extension;
        this.appUri = appUri;
        this.graphId = graphId;
        this.commandSubmitter = commandSubmitter;
        this.messageSubmitter = messageSubmitter;
        this.extensionRunloop = extensionRunloop;
        this.extensionContext = extensionContext;
        log.info("ExtensionEnvImpl created for Extension: {}", extensionId);
    }

    public EngineExtensionContext getExtensionContext() {
        return extensionContext;
    }

    @Override
    public void postTask(Runnable task) {
        extensionRunloop.postTask(task);
    }

    @Override
    public CompletableFuture<CommandResult> sendCmd(Command command) {
        // Extension 发送命令，通过 commandSubmitter 委托给 Engine 处理
        if (commandSubmitter != null) {
            return commandSubmitter.submitCommandFromExtension(command, extensionId);
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "ExtensionCommandSubmitter is null, cannot send command for Extension: %s".formatted(extensionId)));
        }
    }

    @Override
    public void sendResult(CommandResult result) {
        // Extension 发送命令结果，通过 commandSubmitter 委托给 Engine 处理
        if (commandSubmitter != null) {
            commandSubmitter.routeCommandResultFromExtension(result, extensionId);
        } else {
            log.warn(
                    "ExtensionCommandSubmitter is null, cannot route command result for Extension: {}. Result dropped.",
                    extensionId);
        }
    }

    @Override
    public void sendData(DataMessage data) {
        if (messageSubmitter != null) {
            messageSubmitter.submitMessageFromExtension(data, extensionId);
        } else {
            log.warn("ExtensionMessageSubmitter is null, cannot send data for Extension: {}. Message dropped.",
                    extensionId);
        }
    }

    @Override
    public void sendVideoFrame(VideoFrameMessage videoFrame) {
        if (messageSubmitter != null) {
            messageSubmitter.submitMessageFromExtension(videoFrame, extensionId);
        } else {
            log.warn("ExtensionMessageSubmitter is null, cannot send video frame for Extension: {}. Message dropped.",
                    extensionId);
        }
    }

    @Override
    public void sendAudioFrame(AudioFrameMessage audioFrame) {
        if (messageSubmitter != null) {
            messageSubmitter.submitMessageFromExtension(audioFrame, extensionId);
        } else {
            log.warn("ExtensionMessageSubmitter is null, cannot send audio frame for Extension: {}. Message dropped.",
                    extensionId);
        }
    }

    @Override
    public void sendMessage(source.hanger.core.message.Message message) {
        if (messageSubmitter != null) {
            messageSubmitter.submitMessageFromExtension(message, extensionId);
        } else {
            log.warn(
                    "ExtensionMessageSubmitter is null, cannot send message of type {} for Extension: {}. Message dropped.",
                    message.getType(), extensionId);
        }
    }

    @Override
    public Optional<Object> getProperty(String path) {
        return extension.getProperty(path);
    }

    @Override
    public void setProperty(String path, Object value) {
        extension.setProperty(path, value);
    }

    @Override
    public boolean hasProperty(String path) {
        return extension.hasProperty(path);
    }

    @Override
    public void deleteProperty(String path) {
        extension.deleteProperty(path);
    }

    @Override
    public Optional<Integer> getPropertyInt(String path) {
        return extension.getPropertyInt(path);
    }

    @Override
    public void setPropertyInt(String path, int value) {
        extension.setPropertyInt(path, value);
    }

    @Override
    public Optional<Long> getPropertyLong(String path) {
        return extension.getPropertyLong(path);
    }

    @Override
    public void setPropertyLong(String path, long value) {
        extension.setPropertyLong(path, value);
    }

    @Override
    public Optional<String> getPropertyString(String path) {
        return extension.getPropertyString(path);
    }

    @Override
    public void setPropertyString(String path, String value) {
        extension.setPropertyString(path, value);
    }

    @Override
    public Optional<Boolean> getPropertyBool(String path) {
        return extension.getPropertyBool(path);
    }

    @Override
    public void setPropertyBool(String path, boolean value) {
        extension.setPropertyBool(path, value);
    }

    @Override
    public Optional<Double> getPropertyDouble(String path) {
        return extension.getPropertyDouble(path);
    }

    @Override
    public void setPropertyDouble(String path, double value) {
        extension.setPropertyDouble(path, value);
    }

    @Override
    public Optional<Float> getPropertyFloat(String path) {
        return extension.getPropertyFloat(path);
    }

    @Override
    public void setPropertyFloat(String path, float value) {
        extension.setPropertyFloat(path, value);
    }

    @Override
    public void initPropertyFromJson(String jsonStr) {
        extension.initPropertyFromJson(jsonStr);
    }

    @Override
    public String getAppUri() {
        return appUri;
    }

    @Override
    public String getGraphId() {
        return graphId;
    }

    @Override
    public String getExtensionName() {
        return extensionId;
    }

    @Override
    public Extension getAttachedExtension() {
        return extension;
    }

    public Runloop getAttachedRunloop() {
        return extensionRunloop;
    }

    @Override
    public void close() {
        log.info("ExtensionEnvImpl {}: Close requested. Delegating to extension if applicable.", extensionId);
        // Extension 本身没有 stop 方法，其生命周期由 Engine 协调
        // 这里暂时不执行任何关闭操作，因为 close 应该由 Engine 来管理
    }
}