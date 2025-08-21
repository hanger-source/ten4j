package source.hanger.core.extension.component.common;

import lombok.Getter;
import lombok.Setter;

/**
 * 服务处理后的通用输出块抽象基类。
 * 代表了 响应中经过解析和聚合后的高层级逻辑数据。
 */
@Setter
@Getter
public abstract class OutputBlock {
    private String originalMessageId; // 关联触发此 LLM 输出的原始消息 ID

    public OutputBlock(String originalMessageId) {
        this.originalMessageId = originalMessageId;
    }

}
