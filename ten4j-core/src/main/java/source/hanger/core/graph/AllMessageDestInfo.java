package source.hanger.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 对应 C 层的 ten_all_msg_type_dest_info_t 结构，表示所有消息类型的目的地信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class AllMessageDestInfo {
    @JsonProperty("cmd")
    private List<RoutingRuleDefinition> commandRules;

    @JsonProperty("data")
    private List<RoutingRuleDefinition> dataRules;

    @JsonProperty("video_frame")
    private List<RoutingRuleDefinition> videoFrameRules;

    @JsonProperty("audio_frame")
    private List<RoutingRuleDefinition> audioFrameRules;
}