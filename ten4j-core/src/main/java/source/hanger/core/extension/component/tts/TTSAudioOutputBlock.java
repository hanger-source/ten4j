package source.hanger.core.extension.component.tts;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.Setter;
import source.hanger.core.extension.component.common.OutputBlock;

/**
 * TTS 音频输出块。
 * 包含 TTS 生成的音频数据及其相关信息。
 */
@Getter
@Setter
public class TTSAudioOutputBlock extends OutputBlock {

    private final ByteBuffer data;
    private final int sampleRate;
    private final int channels;
    private final int sampleBytes;

    public TTSAudioOutputBlock(ByteBuffer data, String messageId, int sampleRate, int channels, int sampleBytes) {
        super(messageId);
        this.data = data;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.sampleBytes = sampleBytes;
    }
}
