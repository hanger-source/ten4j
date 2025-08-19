package source.hanger.core.extension.system.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ToolMetadata {
    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("parameters")
    private List<ToolParameter> parameters;

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
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
