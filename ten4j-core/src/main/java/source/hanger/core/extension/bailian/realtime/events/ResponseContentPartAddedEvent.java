package source.hanger.core.extension.bailian.realtime.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import source.hanger.core.extension.bailian.realtime.events.response.Part;

/**
 * 表示 Realtime API 的 "response.content_part.added" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("response.content_part.added")
public class ResponseContentPartAddedEvent extends RealtimeEvent {

    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("response_id")
    private String responseId;
    @JsonProperty("item_id")
    private String itemId;
    @JsonProperty("output_index")
    private String outputIndex;
    @JsonProperty("content_index")
    private String contentIndex;
    @JsonProperty("content_type")
    private String contentType;
    private String content;

    @JsonProperty("part")
    private Part part;
}
