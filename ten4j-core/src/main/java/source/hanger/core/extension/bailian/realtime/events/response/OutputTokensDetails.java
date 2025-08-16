package source.hanger.core.extension.bailian.realtime.events.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OutputTokensDetails {
    @JsonProperty("text_tokens")
    private Integer textTokens;
    @JsonProperty("audio_tokens")
    private Integer audioTokens;
}
