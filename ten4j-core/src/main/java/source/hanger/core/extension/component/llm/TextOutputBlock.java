package source.hanger.core.extension.component.llm;

import lombok.Getter;
import lombok.Setter;
import source.hanger.core.extension.component.common.OutputBlock;

/**
 * LLM 服务处理后的文本输出块。
 * 封装 LLM 服务聚合后的文本内容。
 */
@Getter
public class TextOutputBlock extends OutputBlock {
    private final String text;
    private final boolean isFinalSegment;
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
