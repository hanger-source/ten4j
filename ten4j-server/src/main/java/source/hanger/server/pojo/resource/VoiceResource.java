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
public class VoiceResource {

    @JsonProperty("voiceModel")
    private String voiceModel;

    @JsonProperty("voiceModelName")
    private String voiceModelName;

    @JsonProperty("candidates")
    private List<VoiceOption> candidates;
}
