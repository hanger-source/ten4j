package source.hanger.core.extension.dashscope.client.realtime.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import source.hanger.core.extension.dashscope.client.realtime.events.response.Session;

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
