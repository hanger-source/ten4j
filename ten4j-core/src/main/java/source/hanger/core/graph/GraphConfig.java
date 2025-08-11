package source.hanger.core.graph;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 表示 Ten 框架的整体配置，可以包含预定义的图、日志级别等。
 * 对应 property.json 的顶层 "ten" 字段下的内容。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class GraphConfig {

    /**
     * 预定义的图配置列表。
     * 对应 property.json 中的 "predefined_graphs" 数组。
     */
    @JsonProperty("predefined_graphs")
    private List<PredefinedGraphEntry> predefinedGraphs;

    /**
     * 日志配置。
     * 对应 property.json 中的 "log" 字段。
     */
    @JsonProperty("log")
    private LogConfig logConfig;
}
