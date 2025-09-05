package source.hanger.core.extension.dashscope.component.stream;

import java.nio.ByteBuffer;
import java.util.Base64;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.extension.component.tts.BaseTTSStreamAdapter;
import source.hanger.core.extension.component.tts.TTSAudioOutputBlock;
import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;

/**
 * Qwen TTS 流适配器，负责与 DashScope TTS 服务进行交互并处理流式响应。
 */
@Slf4j
public class QwenTTSStreamAdapter extends BaseTTSStreamAdapter<MultiModalConversationResult> {

    private final MultiModalConversation multiModalConversation;

    public QwenTTSStreamAdapter(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        super(interruptionStateProvider, streamPipelineChannel);
        this.multiModalConversation = new MultiModalConversation();
    }

    @Override
    protected Flowable<MultiModalConversationResult> getRawTtsFlowable(TenEnv env, String text) {
        String apiKey = env.getPropertyString("api_key").orElseThrow(() -> new IllegalStateException("No api key found"));
        String voiceName = env.getPropertyString("voice_name").orElseThrow(() -> new IllegalStateException("No voiceName found"));
        String model = env.getPropertyString("model").orElseThrow(() -> new IllegalStateException("No model found"));
        AudioParameters.Voice voice = AudioParameters.Voice.valueOf(voiceName.toUpperCase());

        MultiModalConversationParam param = MultiModalConversationParam.builder()
            .model(model)
            .apiKey(apiKey)
            .text(text)
            .voice(voice)
            .build();

        try {
            return multiModalConversation.streamCall(param);
        } catch (NoApiKeyException | UploadFileException e) {
            log.error("[{}] 调用 DashScope TTS API 出现错误: {}", env.getExtensionName(), e.getMessage(), e);
            return Flowable.error(e);
        }
    }

    @Override
    protected Flowable<PipelinePacket<OutputBlock>> transformSingleTTSResult(MultiModalConversationResult result, Message originalMessage, TenEnv env) {
        if (result.getOutput() != null && result.getOutput().getAudio() != null) {
            byte[] audioData = Base64.getDecoder().decode(result.getOutput().getAudio().getData());
            // 这里将音频数据封装成一个 OutputBlock，具体类型需要根据实际定义
            // 假设我们有一个 AudioOutputBlock，或者直接使用通用 OutputBlock 包含音频数据
            TTSAudioOutputBlock block = new TTSAudioOutputBlock(ByteBuffer.wrap(audioData), originalMessage.getId(), 24000, 2, 1);
            return Flowable.just(new PipelinePacket<>(block, originalMessage));
        } else {
            return null;
        }
    }

}
