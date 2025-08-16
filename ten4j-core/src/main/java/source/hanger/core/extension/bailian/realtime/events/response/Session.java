package source.hanger.core.extension.bailian.realtime.events.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import source.hanger.core.extension.bailian.realtime.messages.InputAudioTranscription;
import source.hanger.core.extension.bailian.realtime.messages.TurnDetection;

/**
 * 会话信息。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Session {
    @JsonProperty("object")
    private String object;
    @JsonProperty("model")
    private String model;
    @JsonProperty("modalities")
    private java.util.List<String> modalities;
    @JsonProperty("instructions")
    private String instructions;
    @JsonProperty("voice")
    private String voice;
    @JsonProperty("input_audio_format")
    private String inputAudioFormat;
    @JsonProperty("output_audio_format")
    private String outputAudioFormat;
    @JsonProperty("input_audio_transcription")
    private InputAudioTranscription inputAudioTranscription;
    @JsonProperty("turn_detection")
    private TurnDetection turnDetection;
    @JsonProperty("tools")
    private java.util.List<Object> tools; // Assuming tools can be any object for now
    @JsonProperty("tool_choice")
    private String toolChoice;
    @JsonProperty("temperature")
    private Double temperature;
    @JsonProperty("max_response_output_tokens")
    private String maxResponseOutputTokens;
    private String id;
    @JsonProperty("request_id")
    private String requestId;
}
