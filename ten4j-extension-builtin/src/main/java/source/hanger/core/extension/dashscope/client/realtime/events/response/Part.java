package source.hanger.core.extension.dashscope.client.realtime.events.response;

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
public class Part {
    @JsonProperty("type")
    private String type;
    @JsonProperty("text")
    private String text;

    @JsonProperty("transcript")
    private String transcript;
}
