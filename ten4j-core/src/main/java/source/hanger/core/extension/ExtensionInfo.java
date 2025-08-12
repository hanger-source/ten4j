package source.hanger.core.extension;

import com.fasterxml.jackson.annotation.JsonProperty;
import source.hanger.core.message.Location;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;
import source.hanger.core.message.MessageConversionContext;
import source.hanger.core.graph.AllMessageDestInfo; // 引入 AllMessageDestInfo

/**
 * Represents runtime information for an Extension instance in the C layer.
 * This class is part of the runtime model, not the static graph configuration.
 * Corresponds to C's ten_extension_info_t structure.
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ExtensionInfo implements BaseExtensionRuntimeInfo {

    @JsonProperty("extension_addon_name")
    private String extensionAddonName;

    // 新增：extensionInstanceName
    @JsonProperty("extension_instance_name")
    private String extensionInstanceName; // 对应 C 语言的 instance_name

    @JsonProperty("extension_group_name")
    private String extensionGroupName;

    @JsonProperty("type") // Added type field, if it's used in C runtime info
    private String type;

    @JsonProperty("loc")
    private Location loc;

    /**
     * Destination information for all message types.
     * Corresponds to C's ten_all_msg_type_dest_info_t structure.
     */
    @JsonProperty("msg_dest_info")
    private AllMessageDestInfo msgDestInfo;

    @JsonProperty("property")
    private Map<String, Object> property; // Corresponds to ten_value_t *property in C

    @JsonProperty("msg_conversion_contexts")
    private List<MessageConversionContext> msgConversionContexts;

    // 新增：全参数构造函数，以匹配 EngineExtensionContext 中的调用
    public ExtensionInfo(String extensionAddonName, String extensionInstanceName,
            String extensionGroupName, Location loc, Map<String, Object> property,
            List<MessageConversionContext> msgConversionContexts,
            AllMessageDestInfo msgDestInfo) { // 补充 msgDestInfo
        this.extensionAddonName = extensionAddonName;
        this.extensionInstanceName = extensionInstanceName; // 初始化 instanceName
        this.extensionGroupName = extensionGroupName;
        this.type = extensionInstanceName; // extensionInstanceName 作为 type
        this.loc = loc;
        this.property = property;
        this.msgConversionContexts = msgConversionContexts;
        this.msgDestInfo = msgDestInfo;
    }

    @Override
    public String getInstanceName() {
        return this.extensionInstanceName;
    }

    @Override
    public String getAddonName() {
        return this.extensionAddonName;
    }

    // getLoc 和 getProperty 已经由 @Data 生成，直接使用
}