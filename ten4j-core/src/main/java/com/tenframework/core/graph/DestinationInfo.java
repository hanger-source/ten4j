package com.tenframework.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 表示路由规则中的一个消息目的地。
 * 对应 property.json 中 connections 下的 dest 数组中的 { "extension": "..." } 对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class DestinationInfo {
    @JsonProperty("extension")
    private String extensionName; // 目标 Extension 的名称
}