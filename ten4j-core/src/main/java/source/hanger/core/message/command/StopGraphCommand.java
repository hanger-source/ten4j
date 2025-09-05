package source.hanger.core.message.command;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import lombok.EqualsAndHashCode;

import static source.hanger.core.message.MessageType.CMD_STOP_GRAPH;

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
@Slf4j
@Getter
@SuperBuilder(toBuilder = true)
public class StopGraphCommand extends Command {

    /**
     * 图ID。
     * 对应C端 `ten_cmd_stop_graph_t` 结构体中的 `graph_id` 字段。
     */
    @JsonProperty("graph_id")
    private String graphId;

    public static StopGraphCommand create(Location srcLoc, List<Location> destLocs, String graphId) {
        return Message.defaultMessage(StopGraphCommand.builder())
            .name(CMD_STOP_GRAPH.name())
            .srcLoc(srcLoc)
            .destLocs(destLocs)
            .graphId(graphId)
            .build();
    }

    @Override
    public MessageType getType() {
        return CMD_STOP_GRAPH;
    }
}