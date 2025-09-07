package source.hanger.core.extension.component.tts;

import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;

/**
 * TTS 流适配器接口。
 * 负责 TTS 文本输入到音频流的转换，并将其转换为更高级的“逻辑块”推送到主管道。
 */
public interface TTSStreamAdapter {

    default void onStart(TenEnv env) {
    }
    /**
     * 处理接收到的语音转录消息，并生成 TTS 音频流。
     *
     * @param env                 当前的 TenEnv 环境。
     * @param speechTranscription speechTranscription。
     * @param originalMessage originalMessage
     */
    void onRequestSpeechTranscription(TenEnv env, String speechTranscription, Message originalMessage);

    void onCancelTTS(TenEnv currentEnv);

    default void onStop(TenEnv env) {
    }
}
