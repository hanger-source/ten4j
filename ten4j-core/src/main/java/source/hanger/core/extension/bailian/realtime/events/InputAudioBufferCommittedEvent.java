package source.hanger.core.extension.bailian.realtime.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "input_audio_buffer.committed" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("input_audio_buffer.committed")
public class InputAudioBufferCommittedEvent extends RealtimeEvent {

    @JsonProperty("event_id")
    private String eventId;
    private String id;
    @JsonProperty("audio_start_ms")
    private Long audioStartMs;
    @JsonProperty("audio_end_ms")
    private Long audioEndMs;
    @JsonProperty("item_id")
    private String itemId;
    @JsonProperty("previous_item_id")
    private String previousItemId;
}
