package com.tenframework.core.tenenv;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.tenframework.core.extension.Extension;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.Message;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.runloop.Runloop;
import lombok.extern.slf4j.Slf4j;

/**
 * @param targetEnv Renamed method Renamed to targetEnv, type is TenEnv
 */
@Slf4j
public record TenEnvProxy<T extends TenEnv>(
        Runloop targetRunloop,
        T targetEnv,
        String signature) implements TenEnv {

    /**
     * 提交一个任务到代理的目标 Runloop。
     * 这是确保所有操作都在正确的线程上下文中执行的关键。
     *
     * @param task 要执行的任务。
     */
    @Override
    public void postTask(Runnable task) {
        targetRunloop.postTask(task);
    }

    // Proxy methods, delegating to targetEnv
    @Override
    public CompletableFuture<CommandResult> sendCmd(Command command) {
        CompletableFuture<CommandResult> future = new CompletableFuture<>();
        targetRunloop.postTask(() -> {
            try {
                // TenEnvProxy 始终委托给 targetEnv 的 sendCmd 方法。
                // 无论是 AppEnvImpl、EngineEnvImpl 还是 ExtensionEnvImpl，其 sendCmd 都是出站命令。
                targetEnv.sendCmd(command)
                        .whenComplete((result, throwable) -> {
                            targetRunloop.postTask(() -> {
                                if (throwable != null) {
                                    future.completeExceptionally(throwable);
                                } else {
                                    future.complete(result);
                                }
                            });
                        });
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override // Implements TenEnv.sendMessage
    public void sendMessage(Message message) {
        targetRunloop.postTask(() -> {
            try {
                // TenEnvProxy 始终委托给 targetEnv 的 sendMessage 方法。
                // 无论是 AppEnvImpl、EngineEnvImpl 还是 ExtensionEnvImpl，其 sendMessage 都是出站消息。
                targetEnv.sendMessage(message);
            } catch (Exception e) {
                log.error("Failed to proxy sendMessage to {}: {}", signature, e.getMessage(), e);
            }
        });
    }

    @Override
    public void sendResult(CommandResult commandResult) {
        targetRunloop.postTask(() -> {
            try {
                targetEnv.sendResult(commandResult);
            } catch (Exception e) {
                log.error("Failed to proxy sendResult to {}: {}. CommandResult {} dropped.",
                        signature, e.getMessage(), commandResult, e); // Changed name to signature
            }
        });
    }

    // Property methods, delegating to targetEnv
    @Override
    public Optional<Object> getProperty(String path) {
        return targetEnv.getProperty(path);
    }

    @Override
    public void setProperty(String path, Object value) {
        targetRunloop.postTask(() -> targetEnv.setProperty(path, value));
    }

    @Override
    public boolean hasProperty(String path) {
        return targetEnv.hasProperty(path);
    }

    @Override
    public void deleteProperty(String path) {
        targetRunloop.postTask(() -> targetEnv.deleteProperty(path));
    }

    @Override
    public Optional<Integer> getPropertyInt(String path) {
        return targetEnv.getPropertyInt(path);
    }

    @Override
    public void setPropertyInt(String path, int value) {
        targetRunloop.postTask(() -> targetEnv.setPropertyInt(path, value));
    }

    @Override
    public Optional<Long> getPropertyLong(String path) {
        return targetEnv.getPropertyLong(path);
    }

    @Override
    public void setPropertyLong(String path, long value) {
        targetRunloop.postTask(() -> targetEnv.setPropertyLong(path, value));
    }

    @Override
    public Optional<String> getPropertyString(String path) {
        return targetEnv.getPropertyString(path);
    }

    @Override
    public void setPropertyString(String path, String value) {
        targetRunloop.postTask(() -> targetEnv.setPropertyString(path, value));
    }

    @Override
    public Optional<Boolean> getPropertyBool(String path) {
        return targetEnv.getPropertyBool(path);
    }

    @Override
    public void setPropertyBool(String path, boolean value) {
        targetRunloop.postTask(() -> targetEnv.setPropertyBool(path, value));
    }

    @Override
    public Optional<Double> getPropertyDouble(String path) {
        return targetEnv.getPropertyDouble(path);
    }

    @Override
    public void setPropertyDouble(String path, double value) {
        targetRunloop.postTask(() -> targetEnv.setPropertyDouble(path, value));
    }

    @Override
    public Optional<Float> getPropertyFloat(String path) {
        return targetEnv.getPropertyFloat(path);
    }

    @Override
    public void setPropertyFloat(String path, float value) {
        targetRunloop.postTask(() -> targetEnv.setPropertyFloat(path, value));
    }

    @Override
    public void initPropertyFromJson(String jsonStr) {
        targetRunloop.postTask(() -> targetEnv.initPropertyFromJson(jsonStr));
    }

    // Send methods, delegating to targetEnv
    @Override
    public void sendData(DataMessage data) {
        targetRunloop.postTask(() -> targetEnv.sendData(data));
    }

    @Override
    public void sendVideoFrame(VideoFrameMessage videoFrame) {
        targetRunloop.postTask(() -> targetEnv.sendVideoFrame(videoFrame));
    }

    @Override
    public void sendAudioFrame(AudioFrameMessage audioFrame) {
        targetRunloop.postTask(() -> targetEnv.sendAudioFrame(audioFrame));
    }

    @Override
    public String getAppUri() {
        return targetEnv.getAppUri();
    }

    @Override
    public String getGraphId() {
        return targetEnv.getGraphId();
    }

    @Override
    public String getExtensionName() {
        return targetEnv.getExtensionName();
    }

    @Override
    public Extension getAttachedExtension() {
        return targetEnv.getAttachedExtension();
    }

    @Override
    public void close() {
        log.info("TenEnvProxy {}: Received close signal. Delegating to target TenEnv.", signature); // Changed name to
                                                                                                    // signature
        targetRunloop.postTask(targetEnv::close);
    }
}