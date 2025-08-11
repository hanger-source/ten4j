package source.hanger.core.extension;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import source.hanger.core.engine.EngineExtensionContext;
import source.hanger.core.extension.submitter.ExtensionCommandSubmitter;
import source.hanger.core.extension.submitter.ExtensionMessageSubmitter;
import source.hanger.core.graph.ExtensionInfo;
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
    private final ExtensionInfo extensionInfo; // 新增：存储 ExtensionInfo

    public ExtensionEnvImpl(Extension extension,
            ExtensionCommandSubmitter commandSubmitter, ExtensionMessageSubmitter messageSubmitter,
            Runloop extensionRunloop, EngineExtensionContext extensionContext, ExtensionInfo extInfo) {
        this.extensionId = extInfo.getLoc().getExtensionName(); // 从 ExtensionInfo 获取
        this.extension = extension;
        this.appUri = extInfo.getLoc().getAppUri(); // 从 ExtensionInfo 获取
        this.graphId = extInfo.getLoc().getGraphId(); // 从 ExtensionInfo 获取
        this.commandSubmitter = commandSubmitter;
        this.messageSubmitter = messageSubmitter;
        this.extensionRunloop = extensionRunloop;
        this.extensionContext = extensionContext;
        this.extensionInfo = extInfo; // 存储 ExtensionInfo
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
                    "ExtensionCommandSubmitter is null, cannot send command for Extension: {}".formatted(extensionId)));
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
        if (extensionInfo == null || extensionInfo.getProperty() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(extensionInfo.getProperty().get(path));
    }

    @Override
    public void setProperty(String path, Object value) {
        if (extensionInfo != null && extensionInfo.getProperty() != null) {
            extensionInfo.getProperty().put(path, value);
        }
    }

    @Override
    public boolean hasProperty(String path) {
        return extensionInfo != null && extensionInfo.getProperty() != null
                && extensionInfo.getProperty().containsKey(path);
    }

    @Override
    public void deleteProperty(String path) {
        if (extensionInfo != null && extensionInfo.getProperty() != null) {
            extensionInfo.getProperty().remove(path);
        }
    }

    @Override
    public Optional<Integer> getPropertyInt(String path) {
        return getProperty(path).filter(Integer.class::isInstance).map(Integer.class::cast);
    }

    @Override
    public void setPropertyInt(String path, int value) {
        setProperty(path, value);
    }

    @Override
    public Optional<Long> getPropertyLong(String path) {
        return getProperty(path).filter(Long.class::isInstance).map(Long.class::cast);
    }

    @Override
    public void setPropertyLong(String path, long value) {
        setProperty(path, value);
    }

    @Override
    public Optional<String> getPropertyString(String path) {
        return getProperty(path).filter(String.class::isInstance).map(String.class::cast);
    }

    @Override
    public void setPropertyString(String path, String value) {
        setProperty(path, value);
    }

    @Override
    public Optional<Boolean> getPropertyBool(String path) {
        return getProperty(path).filter(Boolean.class::isInstance).map(Boolean.class::cast);
    }

    @Override
    public void setPropertyBool(String path, boolean value) {
        setProperty(path, value);
    }

    @Override
    public Optional<Double> getPropertyDouble(String path) {
        return getProperty(path).filter(Double.class::isInstance).map(Double.class::cast);
    }

    @Override
    public void setPropertyDouble(String path, double value) {
        setProperty(path, value);
    }

    @Override
    public Optional<Float> getPropertyFloat(String path) {
        return getProperty(path).filter(Float.class::isInstance).map(Float.class::cast);
    }

    @Override
    public void setPropertyFloat(String path, float value) {
        setProperty(path, value);
    }

    @Override
    public void initPropertyFromJson(String jsonStr) {
        // 这个方法不再直接由外部调用，其功能被 onConfigure 取代
        throw new UnsupportedOperationException("initPropertyFromJson is deprecated. Use onConfigure instead.");
    }

    // 新增：Extension 生命周期方法
    public void onConfigure(Map<String, Object> properties) {
        extensionRunloop.postTask(() -> {
            log.debug("ExtensionEnvImpl {}: Calling onConfigure.", extensionId);
            extension.onConfigure(this, properties);
        });
    }

    public void onInit() {
        extensionRunloop.postTask(() -> {
            log.debug("ExtensionEnvImpl {}: Calling onInit.", extensionId);
            extension.onInit(this);
        });
    }

    public void onStart() {
        extensionRunloop.postTask(() -> {
            log.debug("ExtensionEnvImpl {}: Calling onStart.", extensionId);
            extension.onStart(this);
        });
    }

    public void onStop() {
        extensionRunloop.postTask(() -> {
            log.debug("ExtensionEnvImpl {}: Calling onStop.", extensionId);
            extension.onStop(this);
        });
    }

    public void onDeinit() {
        extensionRunloop.postTask(() -> {
            log.debug("ExtensionEnvImpl {}: Calling onDeinit.", extensionId);
            extension.onDeinit(this);
        });
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
        onStop(); // 在关闭时调用 onStop
        onDeinit(); // 在关闭时调用 onDeinit
        log.info("ExtensionEnvImpl {}: Close requested. Delegating to extension if applicable.", extensionId);
    }
}