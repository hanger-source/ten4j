package source.hanger.core.extension;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 抽象AI服务枢纽扩展，用于简化AI服务相关的扩展开发。
 * 提供了统一的接口用于处理命令、数据、音视频帧，并支持异步命令提交和结果回溯。
 */
@Slf4j
public abstract class AbstractAIServiceHub extends BaseExtension {

    // 移除 @Getter 和 @Setter，因为不再直接持有 CommandSubmitter 引用
    // protected CommandSubmitter commandSubmitter;

    /**
     * 异步Extension上下文
     */
    @Getter
    protected TenEnv asyncExtensionEnv;

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        super.onConfigure(env, properties);
        asyncExtensionEnv = env;
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        super.onCmd(env, command);
        handleAIServiceCommand(env, command);
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage data) { // Renamed from onData
        super.onDataMessage(env, data); // Changed to onDataMessage
        handleAIServiceData(env, data);
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        super.onAudioFrame(env, audioFrame);
        handleAIServiceAudioFrame(env, audioFrame);
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) {
        super.onVideoFrame(env, videoFrame);
        handleAIServiceVideoFrame(env, videoFrame);
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) {
        super.onCmdResult(env, commandResult);
        handleAIServiceCommandResult(env, commandResult);
    }

    /**
     * AI服务特定的命令处理接口
     *
     * @param command 命令消息
     * @param context Extension上下文
     */
    protected abstract void handleAIServiceCommand(TenEnv context, Command command);

    /**
     * AI服务特定的数据处理接口
     *
     * @param data    数据消息
     * @param context Extension上下文
     */
    protected abstract void handleAIServiceData(TenEnv context, DataMessage data);

    /**
     * AI服务特定的音频帧处理接口
     *
     * @param audioFrame 音频帧消息
     * @param context    Extension上下文
     */
    protected abstract void handleAIServiceAudioFrame(TenEnv context, AudioFrameMessage audioFrame);

    /**
     * AI服务特定的视频帧处理接口
     *
     * @param videoFrame 视频帧消息
     * @param context    Extension上下文
     */
    protected abstract void handleAIServiceVideoFrame(TenEnv context, VideoFrameMessage videoFrame);

    /**
     * AI服务特定的命令结果处理接口
     *
     * @param commandResult 命令结果消息
     * @param context       Extension上下文
     */
    protected abstract void handleAIServiceCommandResult(TenEnv context, CommandResult commandResult);

    protected void sendCommandResult(String commandId, Object result, String errorMessage) {
        CommandResult commandResult = CommandResult.fail(commandId, errorMessage); // 使用 CommandResult.fail
        // 根据需要将 result 添加到 properties 中
        if (result != null) {
            commandResult.getProperties().put("result_data", result); // 示例：将结果数据放入properties
        }
        asyncExtensionEnv.sendResult(commandResult);
    }

    /**
     * 异步发送命令并等待结果。
     *
     * @param command 命令对象
     * @return 包含命令结果的CompletableFuture
     */
    protected CompletableFuture<CommandResult> submitCommand(Command command) {
        return asyncExtensionEnv.sendCmd(command); // 通过 asyncExtensionEnv 发送命令
    }
}