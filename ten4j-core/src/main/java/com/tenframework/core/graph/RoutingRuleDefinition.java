package com.tenframework.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 表示特定消息类型（如 cmd, data, audio_frame, video_frame）下的路由规则定义。
 * 对应 property.json 中 connections 数组内部的 "cmd", "data" 等对象中的每个规则。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class RoutingRuleDefinition {
    @JsonProperty("name")
    private String name; // 消息名称或命令名称

    @JsonProperty("dest")
    private List<DestinationInfo> destinations; // 目标 Extension 列表
}