package source.hanger.core.extension.component.common;

import lombok.Getter;
import lombok.Setter;

/**
 * LLM 服务处理后的文本输出块。
 * 封装 LLM 服务聚合后的文本内容。
 */
@Setter
@Getter
public class TextOutputBlock extends OutputBlock {
    private String text;
    private boolean isFinalSegment;
    private String fullText;

    public TextOutputBlock(String originalMessageId, String text, boolean isFinalSegment) {
        super(originalMessageId);
        this.text = text;
        this.isFinalSegment = isFinalSegment;
    }

    public TextOutputBlock(String originalMessageId, String text, boolean isFinalSegment, String fullText) {
        super(originalMessageId);
        this.text = text;
        this.isFinalSegment = isFinalSegment;
        this.fullText = fullText;
    }

}
