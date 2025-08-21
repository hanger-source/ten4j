package source.hanger.core.extension.dashscope.client.realtime.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "conversation.item.input_audio_transcription.completed" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("conversation.item.input_audio_transcription.completed")
public class InputAudioTranscriptionCompletedEvent extends RealtimeEvent {

    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("item_id")
    private String itemId;
    @JsonProperty("transcription_id")
    private String transcriptionId;
    @JsonProperty("content_index")
    private Integer contentIndex;
    private String transcript;
}
