package source.hanger.core.graph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 表示一个消息转换上下文的配置。
 * 对应 C 端 `msg_conversion_context_t` 的概念，
 * 用于定义如何将一种消息转换为另一种消息。
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class MessageConversionContext {

    // 源消息的名称或类型
    @JsonProperty("src_msg_name")
    private String srcMsgName;

    // 目标消息的名称或类型
    @JsonProperty("dst_msg_name")
    private String dstMsgName;

    // 消息转换的类型或策略 (例如 "json_to_json", "proto_to_json" 等)
    @JsonProperty("conversion_type")
    private String conversionType;

    // 具体转换规则的映射，例如字段映射、默认值等
    // 这可以是任意 JSON 结构，需要根据具体的转换器来解析
    @JsonProperty("rules")
    private Map<String, Object> rules;

    // 转换条件 (可选)，例如当某个字段满足特定值时才触发转换
    // 也可以是简单的键值对，或者更复杂的表达式
    @JsonProperty("condition")
    private Map<String, Object> condition;
}