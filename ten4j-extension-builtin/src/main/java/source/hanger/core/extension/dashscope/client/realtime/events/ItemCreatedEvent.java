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
 * 表示 Realtime API 的 "conversation.item.created" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("conversation.item.created")
public class ItemCreatedEvent extends RealtimeEvent {

    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("item_id")
    private String itemId;
    @JsonProperty("previous_item_id")
    private String previousItemId;
    @JsonProperty("item")
    private Item item;
}
