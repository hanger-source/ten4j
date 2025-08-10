package com.tenframework.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class NodeConfig {
    /**
     * 节点的唯一名称 (Extension名称)
     */
    private String name;

    /**
     * 节点对应的Extension类型
     */
    private String type;

    /**
     * 节点私有属性，传递给对应的Extension实例
     */
    private Map<String, Object> properties;

    /**
     * 节点私有环境变量，传递给对应的Extension实例
     */
    @JsonProperty("env_properties")
    private Map<String, String> envProperties;
}