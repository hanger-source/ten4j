package source.hanger.core.message.command;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;

/**
 * 所有命令消息的抽象基类，继承自 Message。
 * 提供命令特有的基本属性和 Jackson 多态序列化/反序列化配置。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public abstract class Command extends Message {

    @JsonProperty("name")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected String name; // 命令名称，用于 Jackson 多态识别

    @JsonProperty("parent_cmd_id")
    protected String parentCommandId; // 新增：父命令ID，对齐C端

    public Command(String id, Location srcLoc, MessageType type, List<Location> destLocs,
        Map<String, Object> properties, long timestamp, String name) {
        super(id, type, srcLoc, destLocs, name, properties, timestamp);
        this.name = name;
    }

    // 修改此构造函数，使其也接收 id
    protected Command(String id, MessageType type, Location srcLoc, List<Location> destLocs, String name) {
        super(id, type, srcLoc, destLocs);
        this.name = name;
    }

    public Command(String id, MessageType type, Location srcLoc, List<Location> destLocs, String name,
        String parentCommandId) {
        super(id, type, srcLoc, destLocs);
        this.name = name;
        this.parentCommandId = parentCommandId;
    }

    public String getOriginalCommandId() {
        return parentCommandId;
    }
}