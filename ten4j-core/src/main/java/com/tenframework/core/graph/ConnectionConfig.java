package com.tenframework.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 表示图中的连接配置，用于定义消息路由规则。
 * 对应 property.json 中 connections 数组中的每个连接对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ConnectionConfig {
    /**
     * 连接的源 Extension 名称。
     * 对应 property.json 中的 "extension" 字段。
     */
    @JsonProperty("extension")
    private String sourceExtensionName;

    /**
     * 命令消息的路由规则列表。
     * 对应 property.json 中的 "cmd" 数组。
     */
    @JsonProperty("cmd")
    private List<RoutingRuleDefinition> commandRules;

    /**
     * 数据消息的路由规则列表。
     * 对应 property.json 中的 "data" 数组。
     */
    @JsonProperty("data")
    private List<RoutingRuleDefinition> dataRules;

    /**
     * 音频帧消息的路由规则列表。
     * 对应 property.json 中的 "audio_frame" 数组。
     */
    @JsonProperty("audio_frame")
    private List<RoutingRuleDefinition> audioFrameRules;

    /**
     * 视频帧消息的路由规则列表。
     * 对应 property.json 中的 "video_frame" 数组。
     */
    @JsonProperty("video_frame")
    private List<RoutingRuleDefinition> videoFrameRules;

    // 移除旧的字段，如 source, destinations, type, condition, routingRules, broadcast,
    // priority, minPriority
}