package source.hanger.core.extension.bailian.realtime.events;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "response.audio_delta" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ResponseAudioDeltaEvent implements RealtimeEvent {
    public static final String TYPE = "response.audio_delta";

    private String id;
    private Integer contentIndex;
    private String format;
    private String sampleRate;
    private String audioData;
    private Boolean isFinal;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("id", id);
        json.addProperty("content_index", contentIndex);
        json.addProperty("format", format);
        json.addProperty("sample_rate", sampleRate);
        json.addProperty("audio_data", audioData);
        json.addProperty("is_final", isFinal);
        return json;
    }
}
