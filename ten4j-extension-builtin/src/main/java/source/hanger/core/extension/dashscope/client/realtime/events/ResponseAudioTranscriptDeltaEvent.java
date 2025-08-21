package source.hanger.core.extension.dashscope.client.realtime.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "response.audio_transcript.delta" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("response.audio_transcript.delta")
public class ResponseAudioTranscriptDeltaEvent extends RealtimeEvent {

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
    @JsonProperty("delta")
    private String delta;
    private String text;
}
