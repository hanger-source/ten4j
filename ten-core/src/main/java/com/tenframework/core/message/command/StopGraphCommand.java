package com.tenframework.core.message.command;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.MessageType;
import com.tenframework.core.util.MessageUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 停止图命令消息，对齐C/Python中的TEN_MSG_TYPE_CMD_STOP_GRAPH。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/cmd_base/cmd_stop_graph/cmd.h
 * (L24-27)
 * ```c
 * typedef struct ten_cmd_stop_graph_t {
 * ten_cmd_base_t cmd_base_hdr; // (基消息头)
 * ten_value_t graph_id; // string // 图ID
 * } ten_cmd_stop_graph_t;
 * ```
 *
 * Java 实现中，我们将 `graph_id` 直接作为类的字段，并通过 `@JsonProperty` 进行映射。
 * 不再需要自定义的 Jackson `JsonSerializer` 和 `JsonDeserializer`，因为其字段可以直接映射。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class StopGraphCommand extends Command {

    /**
     * 图ID。
     * 对应C端 `ten_cmd_stop_graph_t` 结构体中的 `graph_id` 字段。
     */
    @JsonProperty("graph_id")
    private String graphId;

    /**
     * 全参构造函数，用于创建停止图命令消息。
     *
     * @param id         消息ID。
     * @param srcLoc     源位置。
     * @param destLocs   目的位置。
     * @param properties 消息属性。
     * @param timestamp  消息时间戳。
     * @param graphId    图ID。
     */
    public StopGraphCommand(String id, Location srcLoc, List<Location> destLocs,
            Map<String, Object> properties, long timestamp, String graphId) {
        super(id, srcLoc, MessageType.CMD_STOP_GRAPH, destLocs, properties,
            timestamp, MessageType.CMD_STOP_GRAPH.name()); // 修正为调用 Command 的构造函数
        this.graphId = graphId;
    }

    /**
     * 用于内部创建的简化构造函数。
     *
     * @param srcLoc   源位置。
     * @param destLocs 目的位置。
     * @param graphId  图ID。
     */
    public StopGraphCommand(Location srcLoc, List<Location> destLocs, String graphId) {
        super(MessageUtils.generateUniqueId(), srcLoc, MessageType.CMD_STOP_GRAPH, destLocs,
            Collections.emptyMap(), System.currentTimeMillis(),
            MessageType.CMD_STOP_GRAPH.name());
        this.graphId = graphId;
    }

}