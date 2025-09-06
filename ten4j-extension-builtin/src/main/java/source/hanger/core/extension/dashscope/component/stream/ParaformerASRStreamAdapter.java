package source.hanger.core.extension.dashscope.component.stream;

import java.nio.ByteBuffer;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.exception.NoApiKeyException;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.asr.ASRTranscriptionOutputBlock;
import source.hanger.core.extension.component.asr.BaseASRStreamAdapter;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.state.ExtensionStateProvider;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.extension.dashscope.test.AudioFileWriter;
import source.hanger.core.tenenv.TenEnv;

@Slf4j
public class ParaformerASRStreamAdapter extends BaseASRStreamAdapter<RecognitionResult> {

    private final AudioFileWriter audioFileWriter = new AudioFileWriter("output", "output");
    private final Recognition recognition  = new Recognition();

    public ParaformerASRStreamAdapter(
        ExtensionStateProvider extensionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        super(extensionStateProvider, streamPipelineChannel);
    }

    @Override
    protected Flowable<RecognitionResult> getRawAsrFlowable(TenEnv env, Flowable<ByteBuffer> audioInput) {
        String apiKey = env.getPropertyString("api_key")
            .orElseThrow(() -> new IllegalArgumentException("API Key is required for Paraformer ASR."));
        String model = env.getPropertyString("model")
            .orElse("paraformer-realtime-v2");
        try {
            RecognitionParam param = RecognitionParam.builder()
                .apiKey(apiKey)
                .model(model)
                .format("pcm")
                .sampleRate(16000)
                //设置VAD（Voice Activity Detection，语音活动检测）断句的静音时长阈值（单位为ms）。
                //当一段语音后的静音时长超过该阈值时，系统会判定该句子已结束。
                //参数范围为200ms至6000ms，默认值为800ms。
                .parameter("max_sentence_silence", 300)
                // 开关打开时（true）可以防止VAD断句切割过长。默认关闭。
                .parameter("multi_threshold_mode_enabled", true)
                .parameter("language_hints", new String[] {"zh", "en"})
                .build();

            // Use streamCall directly
            Flowable<RecognitionResult> resultFlowable = recognition.streamCall(param, audioInput);

            log.info("[{}] ParaformerASRClient recognition started successfully. channelId={}",
                env.getExtensionName(), streamPipelineChannel.uuid());
            return resultFlowable
                .subscribeOn(Schedulers.io());
        } catch (NoApiKeyException e) {
            log.error("[{}] No API Key provided for Paraformer ASR. channelId={}",
                env.getExtensionName(), streamPipelineChannel.uuid(), e);
            return Flowable.error(e);
        } catch (Exception e) {
            log.error("[{}] Failed to start Paraformer ASR recognition channelId={}",
                env.getExtensionName(), streamPipelineChannel.uuid(), e);
            return Flowable.error(e);
        }
    }

    @Override
    public void onRequestAudioInput(TenEnv env, ByteBuffer rawAudioInput) {
        // 用于排查音频是否正常 audioFileWriter.writeAudioFrame(rawAudioInput);
        super.onRequestAudioInput(env, rawAudioInput);
    }

    @Override
    protected Flowable<PipelinePacket<OutputBlock>> transformSingleRecognitionResult(
        RecognitionResult result,
        TenEnv env
    ) {
        // 从 env 获取 originalMessageId
        String originalMessageId = env.getPropertyString("original_message_id").orElse(null);
        return Flowable.just(
            new PipelinePacket<>(new ASRTranscriptionOutputBlock(result.getRequestId(), originalMessageId, result.getSentence().getText(), result.isSentenceEnd(),
                result.getSentence().getBeginTime(),
                result.getSentence().getEndTime() != null && result.getSentence().getBeginTime() != null
                    ? result.getSentence().getEndTime() - result.getSentence().getBeginTime()
                    : 0L), null)
        );
    }
}
