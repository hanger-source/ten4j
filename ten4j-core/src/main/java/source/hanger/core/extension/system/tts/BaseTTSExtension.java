package source.hanger.core.extension.system.tts;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.system.BaseFlushExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;

/**
 * TTS基础抽象类
 * 基于ten-framework AI_BASE的tts.py设计
 *
 * 核心特性：
 * 1. 异步处理队列机制 (通过 UnicastProcessor 串联所有音频流)
 * 2. 音频数据流式输出
 * 3. 输入数据处理与取消支持
 */
@Slf4j
public abstract class BaseTTSExtension extends BaseFlushExtension<byte[]> {

    public BaseTTSExtension() {
        super();
    }

    /**
     * 启动时调用
     */
    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
    }

    /**
     * 停止时调用，取消所有流
     */
    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
    }

    /**
     * 清理时调用
     */
    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
    }

    /**
     * 新数据消息到来，调用抽象的 onRequestTTS 生成音频流，推送到 processor
     */
    @Override
    public void onDataMessage(TenEnv env, DataMessage data) {
        if (!isRunning) {
            log.warn("[{}] TTS扩展未运行，忽略数据: dataId={}", env.getExtensionName(), data.getId());
            return;
        }

        if (interrupted.get()) {
            log.warn("[{}] 当前扩展未运行或已中断，丢弃消息", env.getExtensionName());
            return;
        }

        Flowable<byte[]> audioFlow = onRequestTTS(env, data);
        if (audioFlow != null) {
            log.debug("[{}] 推送新音频流到streamProcessor, dataId={}", env.getExtensionName(),
                data.getId());
            streamProcessor.onNext(new StreamPayload<>(audioFlow, data));
        }
    }

    /**
     * 发送音频数据块，子类可重写实现具体发送逻辑
     */
    @Override
    protected void handleStreamItem(byte[] audioData, Message originalMessage, TenEnv env) {
        // 默认示例实现，具体发送音频帧
        sendAudioOutput(env, originalMessage, audioData, 24000, 2, 1);
    }

    /**
     * 抽象方法：处理TTS请求，返回音频数据流
     * 子类必须实现
     */
    protected abstract Flowable<byte[]> onRequestTTS(TenEnv env, DataMessage data);

    /**
     * 取消TTS，子类覆盖实现清理逻辑
     */
    @Override
    protected void onCancelFlush(TenEnv env) {
        onCancelTTS(env);
    }

    protected abstract void onCancelTTS(TenEnv env);

    @Override
    public void onCmd(TenEnv env, Command command) {
        super.onCmd(env, command);
    }

    // 发送音频帧
    protected void sendAudioOutput(TenEnv env, Message originalMessage, byte[] audioData,
        int sampleRate, int bytesPerSample,
        int numberOfChannels) {
        try {
            AudioFrameMessage audioFrame = AudioFrameMessage.create("pcm_frame");
            audioFrame.setId(originalMessage.getId()); // 使用原始消息的ID
            audioFrame.setSampleRate(sampleRate);
            audioFrame.setBytesPerSample(bytesPerSample);
            audioFrame.setNumberOfChannel(numberOfChannels);
            audioFrame.setSamplesPerChannel(audioData.length / (bytesPerSample * numberOfChannels));
            audioFrame.setBuf(audioData);
            audioFrame.setType(MessageType.AUDIO_FRAME);
            // 取llm留下来的group_timestamp 也就是llm一组回复
            audioFrame.setProperty("audio_text", originalMessage.getProperty("text"));
            audioFrame.setProperty("group_timestamp", originalMessage.getProperty("group_timestamp"));
            env.sendMessage(audioFrame);
            log.debug("[{}] 发送音频帧成功: size={}", env.getExtensionName(), audioData.length);
        } catch (Exception e) {
            log.error("[{}] 发送音频帧异常: ", env.getExtensionName(), e);
        }
    }

    // 发送错误结果
    protected void sendErrorResult(TenEnv env, String messageId, MessageType messageType, String messageName,
        String errorMessage) {
        String finalMessageId = (messageId != null && !messageId.isEmpty()) ? messageId
            : MessageUtils.generateUniqueId();
        CommandResult errorResult = CommandResult.fail(finalMessageId, messageType, messageName, errorMessage);
        env.sendResult(errorResult);
    }
}
