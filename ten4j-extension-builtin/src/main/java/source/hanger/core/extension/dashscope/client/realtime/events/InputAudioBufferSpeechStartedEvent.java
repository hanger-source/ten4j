package source.hanger.core.extension.dashscope.client.realtime.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "input_audio_buffer.speech_started" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("input_audio_buffer.speech_started")
public class InputAudioBufferSpeechStartedEvent extends RealtimeEvent {

    @JsonProperty("event_id")
    private String eventId;
    private String id;
    @JsonProperty("audio_start_ms")
    private Long audioStartMs;
    @JsonProperty("item_id")
    private String itemId;
}
