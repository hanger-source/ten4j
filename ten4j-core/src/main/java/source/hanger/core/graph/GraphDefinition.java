package source.hanger.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 表示一个消息处理图的静态配置或蓝图。
 * 它不包含任何运行时状态，仅定义图的结构、Extension 配置和连接路由规则。
 * 对应C语言中的ten_graph_t结构体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class GraphDefinition {

    @JsonProperty("graph_id")
    private String graphId; // 图的唯一标识符

    @JsonProperty("app_uri")
    private String appUri; // 关联的 App URI

    @JsonProperty("graph_name")
    private String graphName; // 图的名称

    @JsonProperty("extension_groups_info")
    private List<ExtensionGroupInfo> extensionGroupsInfo;

    @JsonProperty("extensions_info")
    private List<ExtensionInfo> extensionsInfo;

    @JsonProperty("connections")
    private List<ConnectionConfig> connections;

    // 移除旧的 JSON 解析和通用属性映射逻辑

    // Add getter for extensionsInfo (renamed for consistency with error)
    public List<ExtensionInfo> getExtensions() {
        return extensionsInfo;
    }
}