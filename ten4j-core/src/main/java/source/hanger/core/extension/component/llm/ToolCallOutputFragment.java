package source.hanger.core.extension.component.llm;

import lombok.Getter;
import lombok.Setter;

/**
 * 通用工具调用片段。
 * 定义一个与特定 LLM 供应商解耦的工具调用片段结构。
 */
@Setter
@Getter
public class ToolCallOutputFragment {
    // Getters and setters
    private String name;            // 工具名称
    private String argumentsJson;   // 当前片段的参数 (JSON 字符串)
    private String id;              // 工具调用 ID (用于流式聚合)
    private Integer index;          // 片段索引 (用于流式聚合)

    public ToolCallOutputFragment(String name, String argumentsJson, String id, Integer index) {
        this.name = name;
        this.argumentsJson = argumentsJson;
        this.id = id;
        this.index = index;
    }

    // Builder pattern for convenience
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String argumentsJson;
        private String id;
        private Integer index;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder argumentsJson(String argumentsJson) {
            this.argumentsJson = argumentsJson;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public ToolCallOutputFragment build() {
            return new ToolCallOutputFragment(name, argumentsJson, id, index);
        }
    }
}
