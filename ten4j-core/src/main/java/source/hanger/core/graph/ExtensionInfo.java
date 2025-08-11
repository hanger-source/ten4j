package source.hanger.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import source.hanger.core.message.Location;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ExtensionInfo {

    @JsonProperty("extension_addon_name")
    private String extensionAddonName;

    @JsonProperty("extension_group_name")
    private String extensionGroupName;

    @JsonProperty("type") // 新增 type 字段
    private String type;

    @JsonProperty("loc")
    private Location loc;

    /**
     * 所有消息类型的目的地信息。
     * 对应 C 层的 ten_all_msg_type_dest_info_t 结构。
     */
    @JsonProperty("msg_dest_info")
    private AllMessageDestInfo msgDestInfo;

    @JsonProperty("property")
    private Map<String, Object> property; // Corresponds to ten_value_t *property in C

    // TODO: ten_list_t msg_conversion_contexts; 这个字段需要更复杂的映射
    // 暂时用 List<Object> 来表示，后续可能需要独立的 DTO
    @JsonProperty("msg_conversion_contexts")
    private List<MessageConversionContext> msgConversionContexts;
}