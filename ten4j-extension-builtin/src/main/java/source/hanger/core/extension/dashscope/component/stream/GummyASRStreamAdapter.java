package source.hanger.core.extension.dashscope.component.stream;

import java.nio.ByteBuffer;

import com.alibaba.dashscope.audio.asr.translation.TranslationRecognizerParam;
import com.alibaba.dashscope.audio.asr.translation.TranslationRecognizerRealtime;
import com.alibaba.dashscope.audio.asr.translation.results.TranscriptionResult;
import com.alibaba.dashscope.audio.asr.translation.results.TranslationRecognizerResult;
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
import source.hanger.core.tenenv.TenEnv;

@Slf4j
public class GummyASRStreamAdapter extends BaseASRStreamAdapter<TranslationRecognizerResult> {

    private TranslationRecognizerRealtime translator;

    public GummyASRStreamAdapter(
        ExtensionStateProvider extensionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        super(extensionStateProvider, streamPipelineChannel);
    }

    @Override
    public void onStart(TenEnv env) {
        translator = new TranslationRecognizerRealtime();
    }

    @Override
    protected Flowable<TranslationRecognizerResult> getRawAsrFlowable(TenEnv env, Flowable<ByteBuffer> audioInput) {
        String apiKey = env.getPropertyString("api_key")
            .orElseThrow(() -> new IllegalArgumentException("API Key is required for Paraformer ASR."));
        String model = env.getPropertyString("model")
            .orElse("gummy-realtime-v1");

        // 创建TranslationRecognizerParam，audioFrames参数中传入上面创建的Flowable<ByteBuffer>
        TranslationRecognizerParam param =
            TranslationRecognizerParam.builder()
                .apiKey(apiKey)
                .model(model)
                .format("pcm")
                .sampleRate(16000)
                .transcriptionEnabled(true)
                .translationEnabled(false)
                .build();
        try {
            // Use streamCall directly
            Flowable<TranslationRecognizerResult> resultFlowable = translator.streamCall(param, audioInput);

            log.info("[{}] Gummy ASR recognition started successfully. channelId={}",
                env.getExtensionName(), streamPipelineChannel.uuid());
            return resultFlowable
                .subscribeOn(Schedulers.io());
        } catch (NoApiKeyException e) {
            log.error("[{}] No API Key provided for Gummy ASR. channelId={}",
                env.getExtensionName(), streamPipelineChannel.uuid(), e);
            return Flowable.error(e);
        } catch (Exception e) {
            log.error("[{}] Failed to start Gummy ASR recognition channelId={}",
                env.getExtensionName(), streamPipelineChannel.uuid(), e);
            return Flowable.error(e);
        }
    }

    @Override
    public void onRequestAudioInput(TenEnv env, ByteBuffer rawAudioInput) {
        // 用于排查音频是否正常 AudioFileWriter.DEFAULT.writeAudioFrame(rawAudioInput);
        super.onRequestAudioInput(env, rawAudioInput);
    }

    @Override
    protected Flowable<PipelinePacket<OutputBlock>> transformSingleRecognitionResult(
        TranslationRecognizerResult result,TenEnv env) {
        // 从 env 获取 originalMessageId
        String originalMessageId = env.getPropertyString("original_message_id").orElse(null);
        TranscriptionResult transcriptionResult = result.getTranscriptionResult();
        return Flowable.just(
            new PipelinePacket<>(new ASRTranscriptionOutputBlock(result.getRequestId(), originalMessageId,
                transcriptionResult.getText(), transcriptionResult.isSentenceEnd(),
                transcriptionResult.getBeginTime(),
                transcriptionResult.getEndTime() - transcriptionResult.getBeginTime()), null)
        );
    }
}
