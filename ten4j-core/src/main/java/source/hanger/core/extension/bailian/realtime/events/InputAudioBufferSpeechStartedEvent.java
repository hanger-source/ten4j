package source.hanger.core.extension.bailian.realtime.events;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "input.audio_buffer.speech_started" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class InputAudioBufferSpeechStartedEvent implements RealtimeEvent {
    public static final String TYPE = "input.audio_buffer.speech_started";

    private String id;
    private Long audioStartMs; // New field for audio start timestamp

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
