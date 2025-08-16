package source.hanger.core.extension.bailian.realtime.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
