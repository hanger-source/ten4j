package com.tenframework.core.extension;

import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.tenenv.TenEnv;

import java.util.Map;
import java.util.Optional;

/**
 * `Extension` 接口定义了 ten-framework 中 Extension 的生命周期回调和消息处理方法。
 * Extension 是 Engine 内部的业务处理单元，通过 `TenEnvProxy` 与 Engine 异步交互。
 * 它对应 C 语言中的 `ten_extension_t`。
 */
public interface Extension {

    String getExtensionId();

    String getExtensionName();

    String getAppUri(); // 保持此方法，因为它通过 envProxy 获取 AppUri

    /**
     * Extension 的初始化方法。
     *
     * @param extensionId Extension 的唯一 ID。
     * @param properties  Extension 的配置属性。
     * @param env         此 Extension 的 TenEnv 环境句柄。
     */
    default void init(String extensionId, Map<String, Object> properties, TenEnv env) {
        // Default implementation
    }

    /**
     * 当 Extension 配置完成时调用。
     *
     * @param env 此 Extension 的 TenEnv 环境句柄。
     */
    default void onConfigure(TenEnv env) {
        // Default implementation
    }

    /**
     * 当 Extension 初始化完成时调用。
     *
     * @param env 此 Extension 的 TenEnv 环境句柄。
     */
    default void onInit(TenEnv env) {
        // Default implementation
    }

    /**
     * 当 Extension 启动时调用。
     *
     * @param env 此 Extension 的 TenEnv 环境句柄。
     */
    default void onStart(TenEnv env) {
        // Default implementation
    }

    /**
     * 当 Extension 停止时调用。
     *
     * @param env 此 Extension 的 TenEnv 环境句柄。
     */
    default void onStop(TenEnv env) {
        // Default implementation
    }

    /**
     * 当 Extension 去初始化时调用。
     *
     * @param env 此 Extension 的 TenEnv 环境句柄。
     */
    default void onDeinit(TenEnv env) {
        // Default implementation
    }

    /**
     * 当 Extension 被销毁时调用。
     *
     * @param env 此 Extension 的 TenEnv 环境句柄。
     */
    default void destroy(TenEnv env) {
        // Default implementation
    }

    /**
     * 处理传入的命令。
     *
     * @param command 传入的命令。
     * @param env     此 Extension 的 TenEnv 环境句柄。
     */
    default void onCmd(TenEnv env, Command command) {
        // Default implementation
    }

    /**
     * 处理传入的命令结果。
     *
     * @param commandResult 传入的命令结果。
     * @param env           此 Extension 的 TenEnv 环境句柄。
     */
    default void onCmdResult(TenEnv env, CommandResult commandResult) {
        // Default implementation
    }

    /**
     * 处理传入的数据消息。
     *
     * @param dataMessage 传入的数据消息。
     * @param env         此 Extension 的 TenEnv 环境句柄。
     */
    default void onDataMessage(TenEnv env, DataMessage dataMessage) {
        // Default implementation
    }

    /**
     * 处理传入的音频帧消息。
     *
     * @param audioFrame 传入的音频帧消息。
     * @param env        此 Extension 的 TenEnv 环境句柄。
     */
    default void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        // Default implementation
    }

    /**
     * 处理传入的视频帧消息。
     *
     * @param videoFrame 传入的视频帧消息。
     * @param env        此 Extension 的 TenEnv 环境句柄。
     */
    default void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) {
        // Default implementation
    }

    // 新增属性访问方法
    Optional<Object> getProperty(String path);

    void setProperty(String path, Object value);

    boolean hasProperty(String path);

    void deleteProperty(String path);

    Optional<Integer> getPropertyInt(String path);

    void setPropertyInt(String path, int value);

    Optional<Long> getPropertyLong(String path);

    void setPropertyLong(String path, long value);

    Optional<String> getPropertyString(String path);

    void setPropertyString(String path, String value);

    Optional<Boolean> getPropertyBool(String path);

    void setPropertyBool(String path, boolean value);

    Optional<Double> getPropertyDouble(String path);

    void setPropertyDouble(String path, double value);

    Optional<Float> getPropertyFloat(String path);

    void setPropertyFloat(String path, float value);

    void initPropertyFromJson(String jsonStr);
}