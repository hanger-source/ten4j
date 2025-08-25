package source.hanger.core.extension.component.tts;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;

/**
 * TTS 流适配器抽象基类。
 * 负责 TTS 原始输出的复杂解析、音频数据块的生成，并将其转换为更高级的“逻辑块”推送到主管道。
 */
@Slf4j
public abstract class BaseTTSStreamAdapter<RAW_TTS_RESULT> implements TTSStreamAdapter {

    protected final InterruptionStateProvider interruptionStateProvider;
    protected final StreamPipelineChannel<OutputBlock> streamPipelineChannel;

    public BaseTTSStreamAdapter(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        this.interruptionStateProvider = interruptionStateProvider;
        this.streamPipelineChannel = streamPipelineChannel;
    }

    @Override
    public void onRequestSpeechTranscription(TenEnv env, String speechTranscription, Message originalMessage) {
        Flowable<RAW_TTS_RESULT> rawTtsFlowable = getRawTtsFlowable(env, speechTranscription);

        Flowable<PipelinePacket<OutputBlock>> transformedOutputFlowable = rawTtsFlowable
            .flatMap(result -> transformSingleTTSResult(result, originalMessage, env))
            .takeWhile(_ -> !interruptionStateProvider.isInterrupted())
            .doOnError(error -> {
                log.error("[{}] TTS流处理错误. 原始消息ID: {}. 错误: {}", env.getExtensionName(),
                    originalMessage.getId(), error.getMessage(), error);
            })
            .doOnComplete(() -> {
                // 确保所有日志都带有前缀
                log.info("[{}] TTS原始流处理完成. 原始消息ID: {}", env.getExtensionName(),
                    originalMessage.getId());
            });

        streamPipelineChannel.submitStreamPayload(transformedOutputFlowable, env);
    }

    @Override
    public void onCancelTTS(TenEnv currentEnv) {
        log.info("[{}] TTS request cancelled.", currentEnv.getExtensionName());
    }

    /**
     * 抽象方法：获取 TTS 供应商的原始响应流。
     * 由具体实现类提供。
     *
     * @param env  当前的 TenEnv 环境。
     * @param text 要转换为语音的文本。
     * @return 包含原始 TTS 响应的 Flowable 流。
     */
    protected abstract Flowable<RAW_TTS_RESULT> getRawTtsFlowable(TenEnv env, String text);

    /**
     * 抽象方法：从原始 TTS 响应中提取音频数据并转换为 OutputBlock。
     * 由具体实现类提供。
     *
     * @param result       result
     * @param originalMessage 原始语音转录消息。
     * @param env             当前的 TenEnv 环境。
     * @return 转换后的 OutputBlock。
     */
    protected abstract Flowable<PipelinePacket<OutputBlock>> transformSingleTTSResult(RAW_TTS_RESULT result, Message originalMessage, TenEnv env);
}
