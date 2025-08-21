package source.hanger.core.extension.dashscope.client.realtime.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputTextContent {
    @JsonProperty("type")
    private String type;

    @JsonProperty("text")
    private String text;
}
