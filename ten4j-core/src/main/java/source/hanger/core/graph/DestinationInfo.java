package source.hanger.core.graph;

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
    /**
     * 目标 Extension 所在的 App URI。
     * 对应 property.json 中的 "app" 字段。
     */
    @JsonProperty("app")
    private String appUri;

    /**
     * 目标 Extension 所在的 Graph ID。
     * 对应 property.json 中的 "graph" 字段。
     */
    @JsonProperty("graph")
    private String graphId;

    /**
     * 目标 Extension 所属的 Extension Group 名称。
     * 对应 property.json 中的 "extension_group" 字段。
     */
    @JsonProperty("extension_group")
    private String extensionGroupName;

    /**
     * 目标 Extension 的名称。
     * 对应 property.json 中的 "extension" 字段。
     */
    @JsonProperty("extension")
    private String extensionName;

    /**
     * 消息转换配置。
     * 对应 property.json 中的 "msg_conversion" 字段。
     */
    @JsonProperty("msg_conversion")
    private MessageConversionContext msgConversion;
}