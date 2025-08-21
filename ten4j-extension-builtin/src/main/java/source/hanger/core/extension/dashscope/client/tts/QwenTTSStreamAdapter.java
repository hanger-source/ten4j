package source.hanger.core.extension.dashscope.client.tts;

import java.util.Base64;
import java.util.Objects;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.extension.component.tts.BaseTTSStreamAdapter;
import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.extension.component.tts.TTSAudioOutputBlock;

/**
 * Qwen TTS 流适配器，负责与 DashScope TTS 服务进行交互并处理流式响应。
 */
@Slf4j
public class QwenTTSStreamAdapter extends BaseTTSStreamAdapter<MultiModalConversationResult> {

    private static final String MODEL = "qwen-tts";
    private final MultiModalConversation multiModalConversation;

    public QwenTTSStreamAdapter(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        super(interruptionStateProvider, streamPipelineChannel);
        this.multiModalConversation = new MultiModalConversation();
    }

    @Override
    public Flowable<MultiModalConversationResult> getRawTtsFlowable(TenEnv env, String text) {
        String apiKey = env.getPropertyString("api_key").orElseThrow(() -> new IllegalStateException("DashScope API Key 未设置")); // 从 TenEnv 获取 API Key
        String voiceName = env.getPropertyString("voice_name").orElseThrow(() -> new IllegalStateException("DashScope voice_name 未设置")); // 从 TenEnv 获取 voiceName
        AudioParameters.Voice voice = AudioParameters.Voice.valueOf(voiceName.toUpperCase());

        MultiModalConversationParam param = MultiModalConversationParam.builder()
            .model(MODEL)
            .apiKey(apiKey)
            .text(text)
            .voice(voice)
            .build();

        try {
            return multiModalConversation.streamCall(param)
                .subscribeOn(Schedulers.io())  // 指定上游执行线程
                .observeOn(Schedulers.io());    // 指定下游执行线程
        } catch (NoApiKeyException | UploadFileException e) {
            log.error("[{}] 调用 DashScope TTS API 出现错误: {}", env.getExtensionName(), e.getMessage(), e);
            return Flowable.error(e);
        }
    }

    @Override
    public Flowable<PipelinePacket<OutputBlock>> transformSingleTTSResult(MultiModalConversationResult result, Message originalMessage, TenEnv env) {
        if (result.getOutput() != null && result.getOutput().getAudio() != null) {
            byte[] audioData = Base64.getDecoder().decode(result.getOutput().getAudio().getData());
            // 默认参数，需要根据实际情况从 result 中提取或配置
            int sampleRate = 24000; // 假设采样率为 24000 Hz
            int channels = 1;     // 假设声道数为 1 (单声道)
            int sampleBytes = 2;  // 假设采样字节为 2 (16位音频)
            return Flowable.just(new PipelinePacket<>(new TTSAudioOutputBlock(audioData, originalMessage.getId(), sampleRate, channels, sampleBytes), originalMessage));
        } else {
            return Flowable.empty();
        }
    }
}
