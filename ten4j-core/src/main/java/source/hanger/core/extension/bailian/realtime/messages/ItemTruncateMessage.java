package source.hanger.core.extension.bailian.realtime.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemTruncateMessage {
    @JsonProperty("type")
    private String type;

    @JsonProperty("item_id")
    private String itemId;

    @JsonProperty("content_index")
    private Integer contentIndex;

    @JsonProperty("audio_end_ms")
    private Long audioEndMs;
}
