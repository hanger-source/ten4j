package source.hanger.core.extension;

import source.hanger.core.graph.GraphConfig;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

import java.util.Map;
import java.util.Optional;

/**
 * `Extension` 接口定义了 ten-framework 中 Extension 的生命周期回调和消息处理方法。
 * Extension 是 Engine 内部的业务处理单元，通过 `TenEnvProxy` 与 Engine 异步交互。
 * 它对应 C 语言中的 `ten_extension_t`。
 */
public interface Extension {

    /**
     * 当 Extension 配置完成时调用。接收配置属性。
     *
     * @param env        此 Extension 的 TenEnv 环境句柄。
     * @param properties Extension 的配置属性。
     */
    default void onConfigure(TenEnv env, Map<String, Object> properties) {
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
     * @param env     此 Extension 的 TenEnv 环境句柄。
     * @param command 传入的命令。
     */
    default void onCmd(TenEnv env, Command command) {
        // Default implementation
    }

    /**
     * 处理传入的命令结果。
     *
     * @param env           此 Extension 的 TenEnv 环境句柄。
     * @param commandResult 传入的命令结果。
     */
    default void onCmdResult(TenEnv env, CommandResult commandResult) {
        // Default implementation
    }

    /**
     * 处理传入的数据消息。
     *
     * @param env         此 Extension 的 TenEnv 环境句柄。
     * @param dataMessage 传入的数据消息。
     */
    default void onDataMessage(TenEnv env, DataMessage dataMessage) {
        // Default implementation
    }

    /**
     * 处理传入的音频帧。
     *
     * @param env               此 Extension 的 TenEnv 环境句柄。
     * @param audioFrameMessage 传入的音频帧消息。
     */
    default void onAudioFrame(TenEnv env, AudioFrameMessage audioFrameMessage) {
        // Default implementation
    }

    /**
     * 处理传入的视频帧。
     *
     * @param env               此 Extension 的 TenEnv 环境句柄。
     * @param videoFrameMessage 传入的视频帧消息。
     */
    default void onVideoFrame(TenEnv env, VideoFrameMessage videoFrameMessage) {
        // Default implementation
    }

    /**
     * 获取 Extension 的名称。
     *
     * @return Extension 的名称。
     */
    String getExtensionName();

}