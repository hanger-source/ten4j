package source.hanger.core.extension.dashscope.client.asr;

import java.io.File;

import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.asr.ASROutputBlock;
import source.hanger.core.extension.component.asr.ASRTextOutputBlock;
import source.hanger.core.extension.component.asr.BaseASRStreamAdapter;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.tenenv.TenEnv;

import static java.nio.ByteBuffer.wrap;

@Slf4j
public class ParaformerASRStreamAdapter extends BaseASRStreamAdapter<RecognitionResult> {

    private volatile ParaformerASRClient paraformerASRClient;
    private AudioFileWriter audioFileWriter;
    private String apiKey;
    private String model;

    public ParaformerASRStreamAdapter(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<ASROutputBlock> streamPipelineChannel) {
        super(interruptionStateProvider, streamPipelineChannel);
    }

    @Override
    protected Flowable<RecognitionResult> getRawAsrFlowable(TenEnv env) {
        if (paraformerASRClient == null) {
            throw new IllegalStateException("ParaformerASRClient is not initialized.");
        }
        return paraformerASRClient.startRecognitionStream();
    }

    @Override
    protected Flowable<PipelinePacket<ASROutputBlock>> transformSingleRecognitionResult(
        RecognitionResult result,
        TenEnv env
    ) {
        // 从 env 获取 originalMessageId
        String originalMessageId = env.getPropertyString("original_message_id").orElse(null);
        return Flowable.just(
            new PipelinePacket<>(new ASRTextOutputBlock(originalMessageId, result.getSentence().getText(), result.isSentenceEnd(),
                result.getSentence().getBeginTime(),
                result.getSentence().getEndTime() != null && result.getSentence().getBeginTime() != null
                    ? result.getSentence().getEndTime() - result.getSentence().getBeginTime()
                    : 0L), null)
        );
    }

    @Override
    protected void sendAudioFrameToAsrClient(TenEnv env, AudioFrameMessage audioFrame) {
        if (paraformerASRClient != null) {
            try {
                paraformerASRClient.sendAudioFrame(wrap(audioFrame.getBuf()));
            } catch (RuntimeException e) {
                log.error("[{}] Failed to send audio frame via client: {}, attempting reconnect.",
                    env.getExtensionName(),
                    e.getMessage());
                if (!stopped.get()) {
                    onReconnect(env);
                }
            }
        } else {
            log.warn("[{}] ASR client is null, cannot send audio frame. Attempting to reconnect.",
                env.getExtensionName());
            if (!stopped.get()) {
                onReconnect(env);
            }
        }
    }

    @Override
    protected void onClientStop() {
        if (paraformerASRClient != null) {
            paraformerASRClient.stopRecognitionClient();
            paraformerASRClient = null;
        }
        if (audioFileWriter != null) {
            audioFileWriter.close();
            audioFileWriter = null;
        }
    }

    @Override
    protected void onClientInit() { // 移除 TenEnv env 参数
        log.info("[{}] Initializing ParaformerASRClient.", tenEnv.getExtensionName());
        this.apiKey = tenEnv.getPropertyString("api_key").orElse("");
        this.model = tenEnv.getPropertyString("model").orElse("paraformer-realtime-v2");
        this.paraformerASRClient = new ParaformerASRClient(tenEnv.getExtensionName(), apiKey, model);
        log.info("[{}] Initialized ParaformerASRClient with config: apiKey={}, model={}",
            tenEnv.getExtensionName(), "***", model);

        String outputDir = System.getProperty("user.home") + File.separator + "asr_audio_chunks";
        this.audioFileWriter = new AudioFileWriter(tenEnv.getExtensionName(), outputDir);
        log.info("[{}] Initialized AudioFileWriter, saving chunks to: {}", tenEnv.getExtensionName(), outputDir);
    }

    @Override
    public boolean isAlive() {
        // TODO: ParaformerASRClient 缺少一个 isConnected 或 isAlive 方法，暂时返回父类实现
        return super.isAlive();
    }

    @Override
    public int getInputAudioSampleRate() {
        return 16000;
    }
}
