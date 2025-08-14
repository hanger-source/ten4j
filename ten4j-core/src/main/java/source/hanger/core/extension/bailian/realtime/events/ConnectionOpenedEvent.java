package source.hanger.core.extension.bailian.realtime.events;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 连接打开事件。
 * 这是一个内部事件，用于通知客户端连接已成功打开。
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ConnectionOpenedEvent implements RealtimeEvent {
    public static final String TYPE = "connection.opened";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }
}
