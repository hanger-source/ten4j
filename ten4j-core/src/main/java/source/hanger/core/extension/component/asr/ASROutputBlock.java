package source.hanger.core.extension.component.asr;

import source.hanger.core.extension.component.common.OutputBlock;

/**
 * ASR输出块的抽象基类。
 */
public abstract class ASROutputBlock implements OutputBlock {
    protected final String originalMessageId;

    public ASROutputBlock(String originalMessageId) {
        this.originalMessageId = originalMessageId;
    }

    @Override
    public String getOriginalMessageId() {
        return originalMessageId;
    }
}
