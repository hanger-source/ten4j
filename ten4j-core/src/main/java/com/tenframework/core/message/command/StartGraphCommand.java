package com.tenframework.core.message.command;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tenframework.core.graph.ExtensionGroupInfo;
import com.tenframework.core.graph.ExtensionInfo;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.MessageType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class StartGraphCommand extends Command {

    @JsonProperty("long_running_mode")
    private Boolean longRunningMode;

    @JsonProperty("predefined_graph_name")
    private String predefinedGraphName;

    @JsonProperty("extension_groups_info")
    private List<ExtensionGroupInfo> extensionGroupsInfo;

    @JsonProperty("extensions_info")
    private List<ExtensionInfo> extensionsInfo;

    @JsonProperty("graph_json")
    private String graphJson;

    // 兼容 Lombok @NoArgsConstructor 的全参构造函数（为了Jackson）
    // 实际内部创建时使用自定义构造函数
    public StartGraphCommand(String id, Location srcLoc, List<Location> destLocs,
            Map<String, Object> properties, long timestamp,
            Boolean longRunningMode, String predefinedGraphName,
            List<ExtensionGroupInfo> extensionGroupsInfo, List<ExtensionInfo> extensionsInfo, String graphJson) {
        super(id, srcLoc, MessageType.CMD_START_GRAPH, destLocs, properties,
            timestamp, MessageType.CMD_START_GRAPH.name());
        this.longRunningMode = longRunningMode;
        this.predefinedGraphName = predefinedGraphName;
        this.extensionGroupsInfo = extensionGroupsInfo;
        this.extensionsInfo = extensionsInfo;
        this.graphJson = graphJson;
    }

    /**
     * 用于内部创建的构造函数，简化参数。
     *
     * @param longRunningMode 是否是长运行模式。
     */
    public StartGraphCommand(String id, Location srcLoc, List<Location> destLocs, String graphJsonDefinition,
            boolean longRunningMode) {
        super(id, srcLoc, MessageType.CMD_START_GRAPH, destLocs,
            Collections.emptyMap(), System.currentTimeMillis(),
            MessageType.CMD_START_GRAPH.name()); // 修正为调用 Command 的构造函数
        this.longRunningMode = longRunningMode;
        graphJson = graphJsonDefinition;
        // 其他属性可以根据需要设置，或在 Message 的 properties 中进行映射
    }

    // 用于内部创建的构造函数，包含额外消息
    public StartGraphCommand(String id, Location srcLoc, List<Location> destLocs, String message,
            String graphJsonDefinition,
            boolean longRunningMode) {
        super(id, srcLoc, MessageType.CMD_START_GRAPH, destLocs,
            Map.of("message", message), System.currentTimeMillis(),
            MessageType.CMD_START_GRAPH.name()); // 修正为调用 Command 的构造函数
        this.longRunningMode = longRunningMode;
        graphJson = graphJsonDefinition;
    }

    // 辅助方法：获取 graphJsonDefinition (与 C 端字段名对齐)
    public String getGraphJsonDefinition() {
        return graphJson;
    }

    public boolean isLongRunningMode() {
        return longRunningMode != null ? longRunningMode : false; // 默认为 false
    }
}