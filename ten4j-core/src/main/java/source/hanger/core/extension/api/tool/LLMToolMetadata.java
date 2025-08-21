package source.hanger.core.extension.api.tool;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * LLM工具元数据，用于工具调用功能
 */
@Data
@Builder
public class LLMToolMetadata {
    private String name;
    private String description;
    private Map<String, Object> parameters; // 工具参数的JSON Schema
    private List<String> required; // 必填参数列表

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public List<String> getRequired() {
        return required;
    }
}
