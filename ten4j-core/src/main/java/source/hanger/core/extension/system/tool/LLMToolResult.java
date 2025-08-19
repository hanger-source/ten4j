package source.hanger.core.extension.system.tool;

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

    @Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @JsonTypeName("requery")
    class Requery implements LLMToolResult {
        private String content;
    }

    @Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @JsonTypeName("llmresult")
    class LLMResult implements LLMToolResult {
        private String content;
    }
}
