package source.hanger.core.message.command;

import source.hanger.core.message.MessageType;
import source.hanger.core.util.MessageUtils; // 用于生成ID
import source.hanger.core.message.Location;

import java.util.List;

public class OutFlushCommand extends Command {

    public static final String NAME = "out_flush";

    public OutFlushCommand() {
        // 使用 MessageUtils 生成唯一的ID
        super(MessageUtils.generateUniqueId(), MessageType.CMD, null, null, NAME);
    }

    // 如果需要支持自定义ID或父命令ID，可以添加更多构造函数
    public OutFlushCommand(String id) {
        super(id, MessageType.CMD, null, null, NAME);
    }

    public OutFlushCommand(String id, String parentCommandId) {
        super(id, MessageType.CMD, null, null, NAME, parentCommandId);
    }
}