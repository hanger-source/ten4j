package source.hanger.core.extension.bailian.realtime.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "function_call_arguments_done" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class FunctionCallArgumentsDoneEvent implements RealtimeEvent {
    public static final String TYPE = "function_call_arguments_done";

    private String id; // This is the conversation item ID, not call ID
    private String callId; // New field for the actual call ID
    private String name;
    private JsonElement arguments; // arguments can be a complex JSON object

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("id", id);
        json.addProperty("call_id", callId);
        json.addProperty("name", name);
        if (arguments != null) {
            json.add("arguments", arguments);
        }
        return json;
    }
}
