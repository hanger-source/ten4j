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
public class Usage {
    @JsonProperty("total_tokens")
    private Integer totalTokens;
    @JsonProperty("cached_tokens")
    private Integer cachedTokens;
    @JsonProperty("input_tokens")
    private Integer inputTokens;
    @JsonProperty("output_tokens")
    private Integer outputTokens;
    @JsonProperty("input_tokens_details")
    private InputTokensDetails inputTokensDetails;
    @JsonProperty("output_tokens_details")
    private OutputTokensDetails outputTokensDetails;
}
