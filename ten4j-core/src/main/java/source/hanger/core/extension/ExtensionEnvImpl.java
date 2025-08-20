package source.hanger.core.extension;

import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.engine.EngineExtensionContext;
import source.hanger.core.extension.submitter.ExtensionCommandSubmitter;
import source.hanger.core.extension.submitter.ExtensionMessageSubmitter;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandExecutionHandle;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.runloop.Runloop;
import source.hanger.core.tenenv.TenEnv;

/**
 * `ExtensionEnvImpl` 是 `Extension` 组件的 `TenEnv` 接口实现。
 * 它将 `TenEnv` 的操作委托给其持有的 `Extension` 实例及其相关的提交器。
 * 这是一个 Runloop 线程安全的类，因为所有操作都通过 `postTask` 提交到 Runloop。
 */
@Slf4j
@Getter
public class ExtensionEnvImpl implements TenEnv {

    private final String extensionName;
    // 【新增】提供获取 Extension 实例的方法
    private final Extension extension;
    private final String appUri;
    private final String graphId;
    private final ExtensionCommandSubmitter commandSubmitter;
    private final ExtensionMessageSubmitter messageSubmitter;
    private final Runloop extensionRunloop; // Extension 所在的 Runloop
    private final EngineExtensionContext extensionContext;
    // 【更改】提供获取运行时信息实例的方法，现在返回 BaseExtensionRuntimeInfo
    private final BaseExtensionRuntimeInfo runtimeInfo; // 更改类型为 BaseExtensionRuntimeInfo，并更名为 runtimeInfo

    public ExtensionEnvImpl(Extension extension,
        ExtensionCommandSubmitter commandSubmitter, ExtensionMessageSubmitter messageSubmitter,
        Runloop extensionRunloop, EngineExtensionContext extensionContext, BaseExtensionRuntimeInfo runtimeInfo) {
        this.extensionName = runtimeInfo.getInstanceName(); // 从运行时信息获取
        this.extension = extension;
        this.appUri = runtimeInfo.getLoc().getAppUri(); // 从运行时信息获取
        this.graphId = runtimeInfo.getLoc().getGraphId(); // 从运行时信息获取
        this.commandSubmitter = commandSubmitter;
        this.messageSubmitter = messageSubmitter;
        this.extensionRunloop = extensionRunloop;
        this.extensionContext = extensionContext;
        this.runtimeInfo = runtimeInfo; // 存储运行时信息
        log.info("ExtensionEnvImpl created for {}: {}", runtimeInfo.getClass().getSimpleName(), extensionName);
    }

    @Override
    public Runloop getRunloop() {
        return extensionRunloop;
    }

    @Override
    public void postTask(Runnable task) {
        extensionRunloop.postTask(task);
    }

    @Override
    public CommandExecutionHandle<CommandResult> sendAsyncCmd(Command command) {
        // Extension 发送命令，通过 commandSubmitter 委托给 Engine 处理
        if (commandSubmitter != null) {
            // 获取 Engine 返回的原始 CommandExecutionHandle
            CommandExecutionHandle<CommandResult> engineHandle = commandSubmitter.submitCommandFromExtension(command,
                extensionName);
            // 将其调度迁移到 Extension 自身的 Runloop，并返回新的 handle
            return engineHandle.onRunloop(extensionRunloop);
        } else {
            // 如果 commandSubmitter 为空，立即返回一个带有异常的 CommandExecutionHandle
            CommandExecutionHandle<CommandResult> handle = new CommandExecutionHandle<>(extensionRunloop);
            RuntimeException ex = new IllegalStateException(
                "ExtensionCommandSubmitter is null, cannot send command for Extension: %s"
                    .formatted(extensionName));
            handle.closeExceptionally(ex);
            return handle;
        }
    }

    @Override
    public void sendResult(CommandResult result) {
        // Extension 发送命令结果，通过 commandSubmitter 委托给 Engine 处理
        if (commandSubmitter != null) {
            commandSubmitter.routeCommandResultFromExtension(result, extensionName);
        } else {
            log.warn(
                "ExtensionCommandSubmitter is null, cannot route command result for Extension: {}. Result dropped.",
                extensionName);
        }
    }

    @Override
    public void sendData(DataMessage data) {
        if (messageSubmitter != null) {
            messageSubmitter.submitMessageFromExtension(data, extensionName);
        } else {
            log.warn("ExtensionMessageSubmitter is null, cannot send data for Extension: {}. Message dropped.",
                extensionName);
        }
    }

    @Override
    public void sendVideoFrame(VideoFrameMessage videoFrame) {
        if (messageSubmitter != null) {
            messageSubmitter.submitMessageFromExtension(videoFrame, extensionName);
        } else {
            log.warn("ExtensionMessageSubmitter is null, cannot send video frame for Extension: {}. Message dropped.",
                extensionName);
        }
    }

    @Override
    public void sendAudioFrame(AudioFrameMessage audioFrame) {
        if (messageSubmitter != null) {
            messageSubmitter.submitMessageFromExtension(audioFrame, extensionName);
        } else {
            log.warn("ExtensionMessageSubmitter is null, cannot send audio frame for Extension: {}. Message dropped.",
                extensionName);
        }
    }

    @Override
    public void sendMessage(source.hanger.core.message.Message message) {
        if (messageSubmitter != null) {
            messageSubmitter.submitMessageFromExtension(message, extensionName);
        } else {
            log.warn(
                "ExtensionMessageSubmitter is null, cannot send message of type {} for Extension: {}. Message dropped.",
                message.getType(), extensionName);
        }
    }

    @Override
    public Optional<Object> getProperty(String path) {
        if (runtimeInfo == null || runtimeInfo.getProperty() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(runtimeInfo.getProperty().get(path));
    }

    @Override
    public void setProperty(String path, Object value) {
        if (runtimeInfo != null && runtimeInfo.getProperty() != null) {
            runtimeInfo.getProperty().put(path, value);
        }
    }

    @Override
    public boolean hasProperty(String path) {
        return runtimeInfo != null && runtimeInfo.getProperty() != null
            && runtimeInfo.getProperty().containsKey(path);
    }

    @Override
    public void deleteProperty(String path) {
        if (runtimeInfo != null && runtimeInfo.getProperty() != null) {
            runtimeInfo.getProperty().remove(path);
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
            log.debug("ExtensionEnvImpl {}: Calling onConfigure.", extensionName);
            extension.onConfigure(this, properties);
        });
    }

    public void onInit() {
        extensionRunloop.postTask(() -> {
            log.debug("ExtensionEnvImpl {}: Calling onInit.", extensionName);
            extension.onInit(this);
        });
    }

    public void onStart() {
        extensionRunloop.postTask(() -> {
            log.debug("ExtensionEnvImpl {}: Calling onStart.", extensionName);
            extension.onStart(this);
        });
    }

    public void onStop() {
        extensionRunloop.postTask(() -> {
            log.debug("ExtensionEnvImpl {}: Calling onStop.", extensionName);
            extension.onStop(this);
        });
    }

    public void onDeinit() {
        extensionRunloop.postTask(() -> {
            log.debug("ExtensionEnvImpl {}: Calling onDeinit.", extensionName);
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
        return extensionName;
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
        log.info("ExtensionEnvImpl {}: Close requested. Delegating to extension if applicable.", extensionName);
    }
}