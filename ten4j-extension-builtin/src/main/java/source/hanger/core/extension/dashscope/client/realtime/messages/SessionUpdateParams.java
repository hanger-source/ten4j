package source.hanger.core.extension.dashscope.client.realtime.messages;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionUpdateParams {
    @JsonProperty("model")
    private String model;

    @JsonProperty("modalities")
    private List<String> modalities;

    @JsonProperty("voice")
    private String voice;

    @JsonProperty("input_audio_format")
    private String inputAudioFormat;

    @JsonProperty("output_audio_format")
    private String outputAudioFormat;

    @JsonProperty("instructions")
    private String instructions;

    @JsonProperty("input_audio_transcription")
    private InputAudioTranscription inputAudioTranscription;

    @JsonProperty("turn_detection")
    private TurnDetection turnDetection;
}
