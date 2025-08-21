package source.hanger.core.extension.component.asr;

import lombok.Getter;
import source.hanger.core.extension.component.common.OutputBlock;

/**
 * 表示 ASR 文本输出的块。
 */
@Getter
public class ASRTranscriptionOutputBlock extends OutputBlock {
    private final String requestId;
    private final String text;
    private final boolean isFinal;
    private final long startTime;
    private final long duration;


    public ASRTranscriptionOutputBlock(String requestId,
        String originalMessageId,
        String text, boolean isFinal, long startTime, long duration) {
        super(originalMessageId);
        this.requestId = requestId;
        this.text = text;
        this.isFinal = isFinal;
        this.startTime = startTime;
        this.duration = duration;
    }
}
