package source.hanger.core.extension.dashscope.client.realtime.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import source.hanger.core.extension.dashscope.client.realtime.events.response.Item;

/**
 * 表示 Realtime API 的 "response.output_item.done" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("response.output_item.done")
public class ResponseOutputItemDoneEvent extends RealtimeEvent {
    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("response_id")
    private String responseId;
    @JsonProperty("item_id")
    private String itemId;
    @JsonProperty("output_index")
    private String outputIndex;

    @JsonProperty("item")
    private Item item;
}
