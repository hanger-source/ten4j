package com.tenframework.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RoutingRule {
    /**
     * 要匹配的消息属性名称（例如 "name", "properties.someKey"）
     */
    @JsonProperty("property_name")
    private String propertyName;

    /**
     * 匹配操作符（例如 "equals", "contains", "regex"）
     */
    private String operator;

    /**
     * 匹配值
     */
    @JsonProperty("property_value")
    private Object propertyValue;

    /**
     * 如果规则匹配，消息将路由到的目标Extension名称列表。
     * 如果为空，则使用ConnectionConfig.destinations。
     */
    private List<String> targets;

    /**
     * 源位置匹配规则。
     * 可以是 "source_app_uri", "source_graph_id", "source_extension_name" 等。
     */
    @JsonProperty("source_property_name")
    private String sourcePropertyName;

    /**
     * 源位置匹配操作符（例如 "equals", "contains", "regex"）。
     */
    @JsonProperty("source_operator")
    private String sourceOperator;

    /**
     * 源位置匹配值。
     */
    @JsonProperty("source_property_value")
    private Object sourcePropertyValue;
}