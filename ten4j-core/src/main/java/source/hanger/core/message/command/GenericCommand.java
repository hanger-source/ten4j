package source.hanger.core.message.command;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import source.hanger.core.message.Location;
import source.hanger.core.message.MessageType;
import source.hanger.core.util.MessageUtils;

public class GenericCommand extends Command {

    @JsonCreator
    public GenericCommand(@JsonProperty("name") String name) {
        super(MessageUtils.generateUniqueId(), MessageType.CMD, null, null, name);
    }

    public GenericCommand(String id, String name) {
        super(id, MessageType.CMD, null, null, name);
    }

    public GenericCommand(String id, String name, String parentCommandId) {
        super(id, MessageType.CMD, null, null, name, parentCommandId);
    }

    public GenericCommand(String id, MessageType type, Location srcLoc, List<Location> destLocs, String name,
            String parentCommandId) {
        super(id, type, srcLoc, destLocs, name, parentCommandId);
    }

    // 提供一个静态工厂方法，简化创建过程，如果需要
    public static GenericCommand create(String name) {
        return new GenericCommand(name);
    }

    public static GenericCommand create(String name, String originalCommandId) {
        return new GenericCommand(MessageUtils.generateUniqueId(), name, originalCommandId);
    }

    public static GenericCommand create(String name, String originalCommandId, MessageType originalCommandType) {
        // 这里需要更复杂的逻辑来处理原始命令的类型和ID，或者重新设计
        // 暂时只用最简单的 create(name)
        return new GenericCommand(MessageUtils.generateUniqueId(), name, originalCommandId);
    }
}