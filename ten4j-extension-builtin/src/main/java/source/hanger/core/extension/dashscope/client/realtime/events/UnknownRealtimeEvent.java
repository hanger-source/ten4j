package source.hanger.core.extension.dashscope.client.realtime.events;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 未知实时事件，用于捕获所有未显式定义在 {@link RealtimeEvent} 子类型中的事件。
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("unknown")
public class UnknownRealtimeEvent extends RealtimeEvent {

    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("response_id")
    private String responseId;
    private String rawType;

    private Map<String, Object> properties = new HashMap<>();

    @JsonAnySetter
    public void add(String key, Object value) {
        this.properties.put(key, value);
    }
}
