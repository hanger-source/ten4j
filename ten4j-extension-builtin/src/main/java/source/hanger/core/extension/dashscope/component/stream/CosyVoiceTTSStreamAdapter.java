package source.hanger.core.extension.dashscope.component.stream;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.extension.component.tts.BaseTTSStreamAdapter;
import source.hanger.core.extension.component.tts.TTSAudioOutputBlock;
import source.hanger.core.extension.dashscope.component.poolobject.CosyVoiceObjectPool;
import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;

/**
 * Cosy Voice TTS 流适配器，负责与 DashScope Cosy Voice TTS 服务进行交互并处理流式响应。
 */
@Slf4j
public class CosyVoiceTTSStreamAdapter extends BaseTTSStreamAdapter<SpeechSynthesisResult> {

    public CosyVoiceTTSStreamAdapter(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        super(interruptionStateProvider, streamPipelineChannel);
    }

    @Override
    protected Flowable<SpeechSynthesisResult> getRawTtsFlowable(TenEnv env, String text) {
        GenericObjectPool<SpeechSynthesizer> pool = CosyVoiceObjectPool.getInstance(env);

        String apiKey = env.getPropertyString("api_key").orElseThrow(
            () -> new IllegalStateException("No api key found"));
        String voiceName = env.getPropertyString("voice_name").orElseThrow(
            () -> new IllegalStateException("No voiceName found"));
        String model = env.getPropertyString("model").orElseThrow(
            () -> new IllegalStateException("No model found"));
        SpeechSynthesisAudioFormat format = SpeechSynthesisAudioFormat.PCM_24000HZ_MONO_16BIT; // 固定格式

        return Flowable.using(
            () -> { // resourceSupplier: 借用实例 (这里是关键)
                long borrowStartTime = System.nanoTime(); // 记录借用开始时间
                SpeechSynthesizer s = pool.borrowObject(); // 在这里借用
                long borrowEndTime = System.nanoTime(); // 记录借用结束时间
                long borrowDurationMillis = (borrowEndTime - borrowStartTime) / 1_000_000;
                log.info("[{}] [TTS_PERF_DEBUG] 从对象池借用 SpeechSynthesizer 实例耗时: {} ms. (Text: {})",
                    env.getExtensionName(), borrowDurationMillis, text);

                // 在借用后更新参数
                s.updateParamAndCallback(SpeechSynthesisParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .voice(voiceName)
                    .format(format)
                    .build(), null);
                log.debug("[{}] [TTS_PERF_DEBUG] SpeechSynthesizer 实例参数更新完毕. (Text: {})",
                    env.getExtensionName(), text);

                return s;
            },
            s -> { // flowableSupplier
                return s.callAsFlowable(text)
                    .subscribeOn(Schedulers.io()) // 确保 TTS SDK 调用在 IO 线程进行
                    .doOnError(throwable -> {
                        s.getDuplexApi().close(1000, "bye");
                        log.error("[{}] [TTS_PERF_DEBUG] 调用 DashScope Cosy Voice TTS API 错误: {}. (Text: {})",
                            env.getExtensionName(),
                            throwable.getMessage(), text, throwable);
                    }).doOnCancel(() -> {
                        s.getDuplexApi().close(1000, "bye");
                        log.info("[{}] [TTS_PERF_DEBUG] TTS {} request adaptor cancelled. (Text: {})",
                            env.getExtensionName(), text, text);
                    });
            },
            s -> { // disposeResource: 释放资源 (归还到池中)
                pool.returnObject(s);
                log.info("[{}] [TTS_PERF_DEBUG] '{}' 归还 SpeechSynthesizer 实例到对象池. (Text: {})",
                    env.getExtensionName(), text, text);
            }
        );
    }

    @Override
    protected Flowable<PipelinePacket<OutputBlock>> transformSingleTTSResult(SpeechSynthesisResult result, Message originalMessage, TenEnv env) {
        if (result.getAudioFrame() != null && result.getAudioFrame().capacity() > 0) {
            byte[] audioData = new byte[result.getAudioFrame().capacity()];
            result.getAudioFrame().get(audioData); // 将 ByteBuffer 转换为 byte[]
            TTSAudioOutputBlock block = new TTSAudioOutputBlock(audioData, originalMessage.getId(), 24000, 2, 1); // 假设采样率等信息
            log.info("[{}] TTS原始流处理开始. text={} 原始消息ID: {}", env.getExtensionName(),
                originalMessage.getProperty("text"), originalMessage.getId()); // 修改这里
            return Flowable.just(new PipelinePacket<>(block, originalMessage));
        } else {
            return Flowable.empty();
        }
    }
}
