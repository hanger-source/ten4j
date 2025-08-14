package source.hanger.core.extension.bailian.realtime.events;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 连接关闭事件。
 * 这是一个内部事件，用于通知客户端连接已关闭。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ConnectionClosedEvent implements RealtimeEvent {
    public static final String TYPE = "connection.closed";

    private int code;
    private String reason;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("code", code);
        json.addProperty("reason", reason);
        return json;
    }
}
