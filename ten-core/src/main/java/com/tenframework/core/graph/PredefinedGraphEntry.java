package com.tenframework.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 表示预定义图的入口，包含图的名称、是否自动启动以及图的定义。
 * 对应 property.json 中 "predefined_graphs" 数组的每个元素。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class PredefinedGraphEntry {
    @JsonProperty("name")
    private String name;

    @JsonProperty("auto_start")
    private boolean autoStart;

    @JsonProperty("graph")
    private GraphDefinition graphDefinition;
}