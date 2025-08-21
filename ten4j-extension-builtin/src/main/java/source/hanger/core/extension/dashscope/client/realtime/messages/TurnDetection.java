package source.hanger.core.extension.dashscope.client.realtime.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // 仅序列化非空字段
public class TurnDetection {
    @JsonProperty("type")
    private String type;

    @JsonProperty("threshold")
    private Double threshold;

    @JsonProperty("prefix_padding_ms")
    private Integer prefixPaddingMs;

    @JsonProperty("silence_duration_ms")
    private Integer silenceDurationMs;
    @JsonProperty("create_response")
    private Boolean createResponse;
    @JsonProperty("interrupt_response")
    private Boolean interruptResponse;
}
