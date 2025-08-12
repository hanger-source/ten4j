package source.hanger.core.extension.qwen.tts;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.system.LlmConstants;
import source.hanger.core.extension.system.tts.BaseTTSExtension;
import source.hanger.core.message.DataMessage;
import source.hanger.core.tenenv.TenEnv;

/**
 * Qwen TTS 扩展实现。
 * 继承 BaseTTSExtension 并实现具体的 TTS 逻辑。
 */
@Slf4j
public class QwenTtsExtension extends BaseTTSExtension {

    private QwenTtsClient qwenTtsClient;
    private String apiKey;
    private String voiceName; // 语音名称，例如 "CHERRY"

    public QwenTtsExtension() {
        super();
    }

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        super.onConfigure(env, properties);
        log.info("[qwen_tts] Extension configuring: {}", env.getExtensionName());

        apiKey = (String)properties.get("api_key");
        voiceName = (String)properties.get("voice_name"); // 从配置中获取语音名称

        if (apiKey == null || voiceName == null || apiKey.isEmpty() || voiceName.isEmpty()) {
            log.error("[qwen_tts] API Key or Voice Name is not set. Please configure in manifest.json/property.json.");
        }

        qwenTtsClient = new QwenTtsClient(apiKey);
    }

    @Override
    protected void onRequestTTS(TenEnv env, DataMessage data) {
        String inputText = (String)data.getProperty(LlmConstants.DATA_OUT_PROPERTY_TEXT); // 假设文本属性名为 "text"
        Boolean isQuiet = (Boolean)data.getProperty(LlmConstants.DATA_IN_PROPERTY_QUIET); // 假设 quiet 属性名为 "quiet"
        if (isQuiet == null) {
            isQuiet = false; // 默认不静音
        }

        if (inputText == null || inputText.isEmpty()) {
            log.warn("[qwen_tts] Received empty text for TTS, ignoring.");
            return;
        }

        log.info("[qwen_tts] Received TTS request for text: \"{}\"", inputText);

        // 使用 QwenTtsClient 进行流式 TTS 调用
        Boolean finalIsQuiet = isQuiet;
        qwenTtsClient.streamTextToSpeech(inputText, voiceName, new QwenTtsStreamCallback() {
            @Override
            public void onAudioReceived(byte[] audioData) {
                // 将接收到的音频数据发送出去
                // 假设默认参数，实际应从配置或 DashScope 返回中获取
                sendAudioOutput(env, audioData, 16000, 2, 1); // 16kHz, 16bit, mono
            }

            @Override
            public void onComplete() {
                log.info("[qwen_tts] TTS stream completed for text: \"{}\"", inputText);
                // 可以在这里发送一个表示 TTS 完成的消息
                sendTranscriptOutput(env, inputText, finalIsQuiet, "assistant.transcription");
            }

            @Override
            public void onError(Throwable t) {
                log.error("[qwen_tts] TTS stream failed for text: \"{}\": {}", inputText, t.getMessage(), t);
                sendErrorResult(env, data.getId(), data.getType(), data.getName(),
                    "TTS流式生成失败: %s".formatted(t.getMessage()));
            }
        });
    }

    @Override
    protected void onCancelTTS(TenEnv env) {
        log.info("[qwen_tts] TTS request cancelled.");
        // 在这里实现取消当前 TTS 生成的逻辑，如果 QwenTtsClient 支持取消
        // DashScope SDK 目前没有直接的取消方法，这里主要用于标记状态和清理
        interrupted.set(true);
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        interrupted.set(false); // 在启动时重置中断标志
    }
}