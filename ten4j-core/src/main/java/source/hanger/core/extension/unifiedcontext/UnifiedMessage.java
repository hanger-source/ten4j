package source.hanger.core.extension.unifiedcontext;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
public class UnifiedMessage {
    private String role; // "user", "assistant", "system", "tool"
    private String text; // 文本内容
    @Singular("images") // 预留图片 URL 或 Base64 字符串
    private List<String> images;
    @Singular("toolCall")
    private List<UnifiedToolCall> toolCalls;
    private String toolCallId; // 如果是工具结果消息，关联到哪个工具调用
    private Map<String, Object> metadata; // 预留通用字段，避免过度设计
}
