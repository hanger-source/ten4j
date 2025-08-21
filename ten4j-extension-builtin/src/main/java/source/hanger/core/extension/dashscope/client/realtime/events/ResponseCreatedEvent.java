package source.hanger.core.extension.dashscope.client.realtime.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import source.hanger.core.extension.dashscope.client.realtime.events.response.ResponseDetail;

/**
 * 表示 Realtime API 的 "response.created" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("response.created")
public class ResponseCreatedEvent extends RealtimeEvent {
    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("response_id")
    private String responseId;
    @JsonProperty("item_id")
    private String itemId;
    @JsonProperty("response")
    private ResponseDetail response;
}
