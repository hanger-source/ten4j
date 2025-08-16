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
public class ResponseCreateMessage {
    @JsonProperty("type")
    private String type;

    @JsonProperty("response")
    private ResponseCreateParams response;

    @JsonProperty("event_id")
    private String eventId;
}
