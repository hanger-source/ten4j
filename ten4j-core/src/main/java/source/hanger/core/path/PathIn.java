package source.hanger.core.path;

import source.hanger.core.connection.Connection;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message; // 导入 Message
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * `PathIn` 代表一个命令的入站路径，用于追踪命令的来源和上下文。
 * 对应C语言中的 `ten_path_in_t` 结构体。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PathIn extends Path {

    private Message originalMessage; // <-- 对应 C 语言的 ten_shared_ptr_t msg_ref
    private Connection sourceConnection; // <-- 对应 C 语言的 ten_connection_t *connection

    /**
     * 构造函数，用于创建 PathIn 实例。
     *
     * @param commandName      命令名称
     * @param commandId        命令ID
     * @param parentCommandId  父命令ID
     * @param sourceLocation   命令源位置
     * @param originalMessage  原始入站消息
     * @param sourceConnection 消息来自的物理连接
     */
    public PathIn(String commandName, String commandId, String parentCommandId, Location sourceLocation,
            Message originalMessage, Connection sourceConnection) {
        super(commandName, commandId, parentCommandId, sourceLocation);
        this.originalMessage = originalMessage;
        this.sourceConnection = sourceConnection;
    }
}