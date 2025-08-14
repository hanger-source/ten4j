package source.hanger.core.extension.bailian.realtime.events;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "input.audio_buffer.speech_stopped" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class InputAudioBufferSpeechStoppedEvent implements RealtimeEvent {
    public static final String TYPE = "input.audio_buffer.speech_stopped";

    private String id;
    private Long audioEndMs; // New field for audio end timestamp

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("id", id);
        return json;
    }
}
