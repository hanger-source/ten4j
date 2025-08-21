package source.hanger.core.extension.api.tool;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = LLMToolResult.Requery.class, name = "requery"),
    @JsonSubTypes.Type(value = LLMToolResult.LLMResult.class, name = "llmresult")
})
public interface LLMToolResult {

    // 静态工厂方法，用于创建 LLMResult 实例
    static LLMToolResult llmResult(boolean success, String content) {
        return LLMResult.builder().success(success).content(content).build();
    }

    static LLMToolResult llmResult(boolean success, String content, String groupId) {
        return LLMResult.builder().success(success).content(content).groupId(groupId).build();
    }

    // 静态工厂方法，用于创建 Requery 实例
    static LLMToolResult requery(String content) {
        return Requery.builder().content(content).build();
    }

    @Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @JsonTypeName("requery")
    class Requery implements LLMToolResult {
        private String content;
    }

    @Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @JsonTypeName("llmresult")
    public static class LLMResult implements LLMToolResult {
        private boolean success; // 新增：表示操作是否成功
        private String content;
        private String groupId;
    }
}
