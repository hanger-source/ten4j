package com.tenframework.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tenframework.core.log.LogLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @author fuhangbo.hanger.uhfun
 **/ // 假设的 LogConfig 类，用于反序列化 "log" 字段
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
class LogConfig {
    @JsonProperty("level")
    private LogLevel level;
}
