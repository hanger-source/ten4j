package source.hanger.core.extension.bailian.realtime.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import source.hanger.core.extension.bailian.realtime.events.response.Session;
import source.hanger.core.extension.bailian.realtime.messages.InputAudioTranscription;
import source.hanger.core.extension.bailian.realtime.messages.TurnDetection;

/**
 * 表示 Realtime API 的 "session.updated" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("session.updated")
public class SessionUpdatedEvent extends RealtimeEvent {

    @JsonProperty("event_id")
    private String eventId;
    private Session session;

}
