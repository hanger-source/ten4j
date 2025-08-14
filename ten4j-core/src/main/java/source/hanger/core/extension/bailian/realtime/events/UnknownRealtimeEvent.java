package source.hanger.core.extension.bailian.realtime.events;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UnknownRealtimeEvent implements RealtimeEvent {
    public static final String TYPE = "unknown";
    private JsonObject rawMessage;

    @Override
    public String getType() {
        return rawMessage != null && rawMessage.has("type") ? rawMessage.get("type").getAsString() : TYPE;
    }

    @Override
    public JsonObject toJsonObject() {
        return rawMessage;
    }
}
