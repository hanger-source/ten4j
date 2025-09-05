package source.hanger.core.message.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.EqualsAndHashCode;
import source.hanger.core.message.MessageType;

import static source.hanger.core.message.MessageType.CMD_START_GRAPH;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@SuperBuilder(toBuilder = true)
@Getter
public class StartGraphCommand extends Command {

    @JsonProperty("predefined_graph_name")
    private String predefinedGraphName;

    @JsonProperty("graph_json")
    private String graphJson;

    // 辅助方法：获取 graphJsonDefinition (与 C 端字段名对齐)
    public String getGraphJsonDefinition() {
        return graphJson;
    }

    @Override
    public MessageType getType() {
        return CMD_START_GRAPH;
    }
}