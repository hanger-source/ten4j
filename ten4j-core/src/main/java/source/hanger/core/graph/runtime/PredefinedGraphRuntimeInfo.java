package source.hanger.core.graph.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import source.hanger.core.extension.ExtensionGroupInfo;
import source.hanger.core.extension.ExtensionInfo;
import source.hanger.core.engine.Engine; // 引用 Engine 类型，尽管在 Java 端我们不直接持有 C 的指针

import java.util.List;

/**
 * 表示 C 语言中 `ten_predefined_graph_info_t` 结构体的 Java 映射。
 * 这个类是运行时模型的一部分，包含了预定义图在运行时的扁平化信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class PredefinedGraphRuntimeInfo {

    @JsonProperty("name")
    private String name;

    @JsonProperty("auto_start")
    private boolean autoStart;

    @JsonProperty("singleton")
    private boolean singleton;

    // 扁平化的 Extension 信息列表，对应 C 语言 ten_list_t extensions_info
    @JsonProperty("extensions_info")
    private List<ExtensionInfo> extensionsInfo;

    // 扁平化的 ExtensionGroup 信息列表，对应 C 语言 ten_list_t extension_groups_info
    @JsonProperty("extension_groups_info")
    private List<ExtensionGroupInfo> extensionGroupsInfo;

    // 在 Java 端，我们不直接存储 C 的 ten_engine_t* 指针，但可以引用对应的 Java Engine 实例
    // 这个字段可能在 App 中存储，而不是 PredefinedGraphRuntimeInfo 本身
    // @JsonProperty("engine") // 运行时才有的信息，不直接从 JSON 反序列化
    private transient Engine engineInstance; // transient 表示不参与 JSON 序列化/反序列化

    // 可以在此处添加构造函数或工厂方法，用于从 GraphDefinition 和 Engine 运行时信息构建此对象

    public PredefinedGraphRuntimeInfo(String name, boolean autoStart, boolean singleton,
            List<ExtensionInfo> extensionsInfo,
            List<ExtensionGroupInfo> extensionGroupsInfo) {
        this.name = name;
        this.autoStart = autoStart;
        this.singleton = singleton;
        this.extensionsInfo = extensionsInfo;
        this.extensionGroupsInfo = extensionGroupsInfo;
    }
}