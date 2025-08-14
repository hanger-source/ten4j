package source.hanger.core.extension.bailian.realtime.events;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "response.text_delta" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ResponseTextDeltaEvent implements RealtimeEvent {
    public static final String TYPE = "response.text_delta";

    private String delta;
    private String id;
    private Integer contentIndex;
    private Boolean isFinal;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("delta", delta);
        json.addProperty("id", id);
        json.addProperty("content_index", contentIndex);
        json.addProperty("is_final", isFinal);
        return json;
    }
}
