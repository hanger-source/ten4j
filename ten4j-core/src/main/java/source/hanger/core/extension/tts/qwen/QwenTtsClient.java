package source.hanger.core.extension.tts.qwen;

import java.util.Base64;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.JsonUtils;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.qwen.tts.QwenTtsStreamCallback;

/**
 * Qwen TTS 客户端，负责与 DashScope TTS 服务进行交互。
 */
@Slf4j
public class QwenTtsClient {

    private static final String MODEL = "qwen-tts";
    private final String apiKey;
    private final MultiModalConversation multiModalConversation;

    public QwenTtsClient(String apiKey) {
        this.apiKey = apiKey;
        this.multiModalConversation = new MultiModalConversation();
    }

    /**
     * 执行流式 TTS 文本转语音。
     *
     * @param text      要转换的文本
     * @param voiceName 语音名称 (例如: AudioParameters.Voice.CHERRY)
     * @param callback  TTS 流式回调接口
     */
    public void streamTextToSpeech(String text, String voiceName, QwenTtsStreamCallback callback) {
        try {
            AudioParameters.Voice voice = AudioParameters.Voice.valueOf(voiceName.toUpperCase()); // 转换为枚举

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                .model(MODEL)
                .apiKey(apiKey)
                .text(text)
                .voice(voice)
                .build();

            Flowable<MultiModalConversationResult> resultFlowable = multiModalConversation.streamCall(param);

            resultFlowable.blockingForEach(r -> {
                log.debug("DashScope TTS Stream Result: {}", JsonUtils.toJson(r));
                if (r.getOutput() != null && r.getOutput().getAudio() != null) {
                    // 音频数据是 base64 编码的 String，需要解码为 byte[]
                    callback.onAudioReceived(Base64.getDecoder().decode(r.getOutput().getAudio().getData()));
                }
                if (r.getOutput() != null && r.getOutput().getFinishReason() != null) {
                    callback.onComplete();
                }
            });

        } catch (ApiException | NoApiKeyException | UploadFileException e) {
            log.error("DashScope TTS 流式调用异常: {}", e.getMessage(), e);
            callback.onError(e);
        } catch (IllegalArgumentException e) {
            log.error("TTS语音名称无效: {}", voiceName, e);
            callback.onError(new RuntimeException("无效的TTS语音名称: " + voiceName, e));
        } catch (Exception e) {
            log.error("DashScope TTS 流式调用未知异常: {}", e.getMessage(), e);
            callback.onError(e);
        }
    }
}