package source.hanger.core.graph;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 表示一个消息处理图的静态配置或蓝图。
 * 它不包含任何运行时状态，仅定义图的结构、Extension 配置和连接路由规则。
 * 对应 property.json 中 "graph" 字段的内容，以及 Rust 中的 Graph 结构体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class GraphDefinition {

    @JsonProperty("graph_id")
    private String graphId; // 图的唯一标识符，配置中可以指定

    @JsonProperty("app_uri")
    private String appUri; // 关联的 App URI，配置中可以指定

    @JsonProperty("graph_name")
    private String graphName; // 图的名称，配置中可以指定

    // 映射 property.json 中的 "nodes" 数组。
    // 每个 NodeDefinition 可以是 Extension 类型或 ExtensionGroup 类型。
    @JsonProperty("nodes")
    private List<NodeDefinition> nodes;

    // 映射 property.json 中的 "connections" 数组。
    @JsonProperty("connections")
    private List<ConnectionDefinition> connections;

    private Map<String, Object> property;
}