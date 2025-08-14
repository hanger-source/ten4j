package source.hanger.core.extension.bailian.realtime.events;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "input.audio_transcription_completed" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class InputAudioTranscriptionCompletedEvent implements RealtimeEvent {
    public static final String TYPE = "input.audio_transcription_completed";

    private String transcript;
    private String id;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("transcript", transcript);
        json.addProperty("id", id);
        return json;
    }
}
