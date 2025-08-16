package source.hanger.core.extension.bailian.realtime.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "input_audio_buffer.speech_stopped" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("input_audio_buffer.speech_stopped")
public class InputAudioBufferSpeechStoppedEvent extends RealtimeEvent {

    @JsonProperty("event_id")
    private String eventId;
    private String id;
    @JsonProperty("audio_end_ms")
    private Long audioEndMs;
    @JsonProperty("item_id")
    private String itemId;
}
