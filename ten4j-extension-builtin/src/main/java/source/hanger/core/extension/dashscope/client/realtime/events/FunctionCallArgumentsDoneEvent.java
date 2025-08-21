package source.hanger.core.extension.dashscope.client.realtime.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "response.function_call_arguments.done" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("response.function_call_arguments.done")
public class FunctionCallArgumentsDoneEvent extends RealtimeEvent {

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
    @JsonProperty("tool_name")
    private String toolName;
    @JsonProperty("function_name")
    private String functionName;
    @JsonProperty("arguments")
    private java.util.Map<String, Object> arguments;
}
