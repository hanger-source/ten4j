package source.hanger.core.extension.dashscope.client.realtime.messages;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseCreateParams {
    @JsonProperty("instructions")
    private String instructions;

    @JsonProperty("modalities")
    private List<String> modalities;
}
