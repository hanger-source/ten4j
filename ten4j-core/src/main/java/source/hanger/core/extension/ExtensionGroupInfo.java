package source.hanger.core.extension;

import com.fasterxml.jackson.annotation.JsonProperty;
import source.hanger.core.message.Location;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * Represents runtime information for an ExtensionGroup instance in the C layer.
 * This class is part of the runtime model, not the static graph configuration.
 * Corresponds to C's ten_extension_group_info_t structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ExtensionGroupInfo implements BaseExtensionRuntimeInfo {

    @JsonProperty("extension_group_addon_name")
    private String extensionGroupAddonName;

    @JsonProperty("extension_group_instance_name")
    private String extensionGroupInstanceName;

    @JsonProperty("loc")
    private Location loc;

    @JsonProperty("property")
    private Map<String, Object> property;

    @Override
    public String getInstanceName() {
        return this.extensionGroupInstanceName;
    }

    @Override
    public String getAddonName() {
        return this.extensionGroupAddonName;
    }

    // getLoc 和 getProperty 已经由 @Data 生成，直接使用
}