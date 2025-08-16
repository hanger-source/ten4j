package source.hanger.core.extension.bailian.realtime.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "response.audio_transcript.done" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("response.audio_transcript.done")
public class ResponseAudioTranscriptDoneEvent extends RealtimeEvent {
    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("response_id")
    private String responseId;
    @JsonProperty("item_id")
    private String itemId;
    @JsonProperty("output_index")
    private String outputIndex;
    @JsonProperty("content_index")
    private String contentIndex;
    @JsonProperty("transcript")
    private String transcript;
}
