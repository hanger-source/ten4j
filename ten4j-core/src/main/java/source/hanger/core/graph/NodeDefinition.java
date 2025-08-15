package source.hanger.core.graph;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 表示图中的一个节点定义（Extension 或 Subgraph）。
 * 对应 property.json 中 "nodes" 数组的每个元素，以及 C 底层 (Rust 实现) 的 GraphNode 结构体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class NodeDefinition {
    /**
     * 节点的唯一名称 (Extension名称 或 Subgraph名称)。
     * 对应 property.json 中的 "name" 字段。
     */
    private String name;

    /**
     * 节点类型，可以是 "extension", "subgraph", "selector"。
     * 对应 property.json 中的 "type" 字段。
     */
    private String type;

    /**
     * 节点对应的Addon名称 (ExtensionAddon名称)。
     * 对应 property.json 中的 "addon" 字段。
     */
    @JsonProperty("addon")
    private String addonName;

    /**
     * 如果是 Extension 类型节点，表示其所属的 ExtensionGroup 名称。
     * 对应 property.json 中的 "extension_group" 字段。
     */
    @JsonProperty("extension_group")
    private String extensionGroupName;

    /**
     * 节点私有属性，传递给对应的 Extension 或 Subgraph 实例。
     * 对应 property.json 中的 "property" 字段。
     */
    private Map<String, Object> property;
}