package source.hanger.core.extension.bailian.realtime.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionCallOutputItem implements RealtimeItem {
    @JsonProperty("type")
    private String type;

    @JsonProperty("call_id")
    private String callId;

    @JsonProperty("output")
    private java.util.Map<String, Object> output;
}
