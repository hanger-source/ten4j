package source.hanger.core.extension.dashscope.client.tts;

import java.util.Base64;
import java.util.Objects;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;

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
     */
    public Flowable<byte[]> streamTextToSpeech(String text, String voiceName) {
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
                .observeOn(Schedulers.io())    // 指定下游执行线程
                .map(r -> {
                    if (r.getOutput() != null && r.getOutput().getAudio() != null) {
                        return Base64.getDecoder().decode(r.getOutput().getAudio().getData());
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .takeUntil(r -> {
                    // 结束条件，检测finishReason不为null表示结束
                    // 由于map后流的是byte[]，这里无法检测finishReason，可以改为doOnNext里检测状态，或者用flatMap做拆分
                    // 简化示例，暂时不实现结束控制，需要上游控制
                    return false;
                });
        } catch (NoApiKeyException | UploadFileException e) {
            return Flowable.error(e);
        }
    }

}