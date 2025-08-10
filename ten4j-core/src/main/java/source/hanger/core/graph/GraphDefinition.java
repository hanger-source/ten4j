package source.hanger.core.graph;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.emptyList;

/**
 * 表示一个消息处理图的静态配置或蓝图。
 * 它不包含任何运行时状态，仅定义图的结构、Extension 配置和连接路由规则。
 * 对应C语言中的ten_graph_t结构体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Slf4j
public class GraphDefinition {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    // 新增字段用于存储原始 JSON 内容
    private String jsonContent;

    // 用于存储其他未显式映射的属性
    private Map<String, Object> properties = new ConcurrentHashMap<>();

    public GraphDefinition(String appUri, String graphJsonDefinition) {
        this.appUri = appUri;
        this.jsonContent = graphJsonDefinition; // 保存原始 JSON
        // Initialize properties map
        this.properties = new ConcurrentHashMap<>();

        try {
            // 使用 ObjectMapper 直接解析到自身，并通过 @JsonAnySetter 自动填充 properties
            GraphDefinition temp = OBJECT_MAPPER.readValue(graphJsonDefinition, GraphDefinition.class);
            this.graphId = Optional.ofNullable(temp.graphId)
                    .orElse(UUID.randomUUID().toString());
            this.graphName = Optional.ofNullable(temp.graphName)
                    .orElse("" + graphId);
            this.extensionGroupsInfo = Optional.ofNullable(temp.extensionGroupsInfo).orElse(new ArrayList<>());
            this.extensionsInfo = Optional.ofNullable(temp.extensionsInfo).orElse(new ArrayList<>());
            this.connections = Optional.ofNullable(temp.connections).orElse(new ArrayList<>());
            // Directly copy properties, @JsonAnySetter handles initial parsing
            this.properties.putAll(temp.properties);

        } catch (JsonProcessingException e) {
            log.error("解析 Graph JSON 定义失败: {}", e.getMessage(), e);
            // 出现解析错误时，提供默认或空的列表，并生成随机 ID
            this.graphId = UUID.randomUUID().toString();
            this.graphName = "InvalidGraph-" + this.graphId;
            this.extensionGroupsInfo = new ArrayList<>();
            this.extensionsInfo = new ArrayList<>();
            this.connections = new ArrayList<>();
            this.properties = new ConcurrentHashMap<>();
        } catch (Exception e) {
            log.error("处理 Graph JSON 定义时发生未知错误: {}", e.getMessage(), e);
            this.graphId = UUID.randomUUID().toString();
            this.graphName = "ErrorGraph-" + this.graphId;
            this.extensionGroupsInfo = new ArrayList<>();
            this.extensionsInfo = new ArrayList<>();
            this.connections = new ArrayList<>();
            this.properties = new ConcurrentHashMap<>();
        }
    }

    @JsonAnyGetter
    public Map<String, Object> any() {
        return properties;
    }

    @JsonAnySetter
    public void set(String name, Object value) {
        // Only put if it's not one of the explicitly mapped fields
        if (!"graph_id".equals(name) &&
                !"app_uri".equals(name) &&
                !"graph_name".equals(name) &&
                !"extension_groups_info".equals(name) &&
                !"extensions_info".equals(name) &&
                !"connections".equals(name) &&
                !"jsonContent".equals(name)) {
            properties.put(name, value);
        }
    }

    // Add getter for extensionsInfo (renamed for consistency with error)
    public List<ExtensionInfo> getExtensions() {
        return extensionsInfo;
    }

    // Add getter for properties, returning a GraphConfig instance
    public GraphConfig getProperties() {
        return new GraphConfig(properties);
    }
}