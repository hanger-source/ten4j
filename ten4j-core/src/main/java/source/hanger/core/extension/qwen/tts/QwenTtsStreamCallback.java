package source.hanger.core.extension.qwen.tts;

/**
 * Qwen TTS 流式回调接口。
 * 用于处理从 DashScope TTS 服务接收到的音频数据和事件。
 */
public interface QwenTtsStreamCallback {

    /**
     * 当接收到新的音频数据块时调用。
     *
     * @param audioData 音频数据的字节数组
     */
    void onAudioReceived(byte[] audioData);

    /**
     * 当 TTS 流完成时调用。
     */
    void onComplete();

    /**
     * 当 TTS 流发生错误时调用。
     *
     * @param t 抛出的异常
     */
    void onError(Throwable t);
}