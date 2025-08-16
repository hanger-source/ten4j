package source.hanger.core.extension.bailian.realtime.events.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ResponseDetail {
    @JsonProperty("id")
    private String id;
    @JsonProperty("object")
    private String object;
    @JsonProperty("conversation_id")
    private String conversationId;
    @JsonProperty("status")
    private String status;
    @JsonProperty("modalities")
    private List<String> modalities;
    @JsonProperty("voice")
    private String voice;
    @JsonProperty("output_audio_format")
    private String outputAudioFormat;
    @JsonProperty("output")
    private List<Item> output;
    @JsonProperty("usage")
    private Usage usage;
    @JsonProperty("status_details")
    private StatusDetails statusDetails;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class StatusDetails {
        @JsonProperty("code")
        private Integer code;
        @JsonProperty("reason")
        private String reason;
    }
}
