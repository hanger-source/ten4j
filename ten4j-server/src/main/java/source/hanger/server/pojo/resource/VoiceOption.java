package source.hanger.server.pojo.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class VoiceOption {

    @JsonProperty("name")
    private String name;

    @JsonProperty("voice")
    private String voice;

    @JsonProperty("tag")
    private List<String> tag;

    @JsonProperty("previewAudioUrl")
    private String previewAudioUrl;

    @JsonProperty("feature")
    private String feature;
}
