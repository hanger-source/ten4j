package source.hanger.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import source.hanger.core.message.Location;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ExtensionGroupInfo {

    // 移除手动构造函数，让 Lombok 的 @AllArgsConstructor 自动生成
    // public ExtensionGroupInfo(String extensionGroupInstanceName, Map<String,
    // Object> property) {
    // this.extensionGroupInstanceName = extensionGroupInstanceName;
    // this.property = property;
    // }

    @JsonProperty("extension_group_addon_name")
    private String extensionGroupAddonName;

    @JsonProperty("extension_group_instance_name")
    private String extensionGroupInstanceName;

    @JsonProperty("loc")
    private Location loc;

    @JsonProperty("property")
    private Map<String, Object> property; // Corresponds to ten_value_t *property in C
}