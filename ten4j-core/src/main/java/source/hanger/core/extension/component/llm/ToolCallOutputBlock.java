package source.hanger.core.extension.component.llm;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import source.hanger.core.extension.component.common.OutputBlock;

/**
 * LLM 工具执行的最终结果块。
 * 封装 LLM Agent 执行工具后的最终结果。
 */
@Getter
public class ToolCallOutputBlock extends OutputBlock {
    private final String toolName;        // 工具名称
    private final String argumentsJson;   // 工具调用时的参数 (JSON 字符串)
    private final String toolResultJson;  // 工具执行的返回结果 (JSON 字符串)
    private final String id;      // DashScope 等 LLM 提供的工具调用 ID (如果适用)
    private final Map<String, Object> toolMetadata;

    public ToolCallOutputBlock(String originalMessageId, String toolName, String argumentsJson, String toolResultJson,
        String id) {
        super(originalMessageId);
        this.toolName = toolName;
        this.argumentsJson = argumentsJson;
        this.toolResultJson = toolResultJson;
        this.id = id;
        toolMetadata = new java.util.HashMap<>();
    }

    public void addToolMetadata(String key, Object value) {
        this.toolMetadata.put(key, value);
    }

    public Object getToolMetadata(String key) {
        return this.toolMetadata.get(key);
    }

}
