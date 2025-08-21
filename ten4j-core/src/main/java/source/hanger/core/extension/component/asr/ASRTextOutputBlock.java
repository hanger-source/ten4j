package source.hanger.core.extension.component.asr;

/**
 * 表示 ASR 文本输出的块。
 */
public class ASRTextOutputBlock extends ASROutputBlock {
    private final String text;
    private final boolean isFinal;
    private final long startTime;
    private final long duration;

    public ASRTextOutputBlock(String originalMessageId, String text, boolean isFinal, long startTime, long duration) {
        super(originalMessageId);
        this.text = text;
        this.isFinal = isFinal;
        this.startTime = startTime;
        this.duration = duration;
    }

    public String getText() {
        return text;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getDuration() {
        return duration;
    }
}
