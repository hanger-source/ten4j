package source.hanger.core.extension.base.tool;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LLMToolMetadata {
    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("parameters")
    private List<ToolParameter> parameters;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ToolParameter {
        @JsonProperty("name")
        private String name;
        @JsonProperty("type")
        private String type;
        @JsonProperty("description")
        private String description;
        @JsonProperty("required")
        private boolean required;
    }
}
