package source.hanger.core.extension.bailian.realtime.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import source.hanger.core.extension.bailian.realtime.events.response.Item;

import java.util.List;

/**
 * 表示 Realtime API 的 "response.output_item.added" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("response.output_item.added")
public class ResponseOutputItemAddedEvent extends RealtimeEvent {

    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("response_id")
    private String responseId;
    @JsonProperty("item_id")
    private String itemId;
    @JsonProperty("output_index")
    private String outputIndex;
    @JsonProperty("output_type")
    private String outputType;
    @JsonProperty("content_parts")
    private List<Object> contentParts;

    @JsonProperty("item")
    private Item item;

}
