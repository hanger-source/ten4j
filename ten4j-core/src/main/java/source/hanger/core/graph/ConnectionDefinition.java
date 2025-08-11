package source.hanger.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map; // 需要这个，因为 RoutingRuleDefinition 内部可能需要

/**
 * 表示图中的连接定义，用于定义消息路由规则。
 * 对应 property.json 中 connections 数组中的每个连接对象，以及 C 底层 (Rust 实现) 的 GraphConnection
 * 结构体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ConnectionDefinition {
    /**
     * 连接的源 Extension 名称。
     * 对应 property.json 中的 "extension" 字段。
     */
    @JsonProperty("extension")
    private String extension; // 重命名为 extension

    /**
     * 命令消息的路由规则列表。
     * 对应 property.json 中的 "cmd" 数组。
     */
    @JsonProperty("cmd")
    private List<RoutingRuleDefinition> cmd; // 重命名为 cmd

    /**
     * 数据消息的路由规则列表。
     * 对应 property.json 中的 "data" 数组。
     */
    @JsonProperty("data")
    private List<RoutingRuleDefinition> data; // 重命名为 data

    /**
     * 音频帧消息的路由规则列表。
     * 对应 property.json 中的 "audio_frame" 数组。
     */
    @JsonProperty("audio_frame")
    private List<RoutingRuleDefinition> audioFrame; // 重命名为 audioFrame

    /**
     * 视频帧消息的路由规则列表。
     * 对应 property.json 中的 "video_frame" 数组。
     */
    @JsonProperty("video_frame")
    private List<RoutingRuleDefinition> videoFrame; // 重命名为 videoFrame
}