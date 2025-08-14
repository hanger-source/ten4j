package source.hanger.core.extension.bailian.realtime.events;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "session.created" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class SessionCreatedEvent implements RealtimeEvent {
    public static final String TYPE = "session.created";

    private Session session;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        if (session != null) {
            json.add("session", session.toJsonObject());
        }
        return json;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @ToString
    public static class Session {
        private String id;

        public JsonObject toJsonObject() {
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            return json;
        }
    }
}
