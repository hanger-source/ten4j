package source.hanger.core.message.command;

import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.message.MessageType;
import lombok.EqualsAndHashCode;

/**
 * 关闭应用命令消息，对齐C/Python中的TEN_MSG_TYPE_CMD_CLOSE_APP。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/cmd_base/cmd_close_app/cmd.h
 * (L24-26)
 * ```c
 * typedef struct ten_cmd_close_app_t {
 * ten_cmd_base_t cmd_base_hdr; // (基消息头，对应 CommandMessage 基类字段)
 * } ten_cmd_close_app_t;
 * ```
 *
 * **重要提示：**
 * - C端 `ten_cmd_close_app_t` 结构体中**没有**额外的硬编码字段。
 * 其语义完全由 `TEN_MSG_TYPE_CMD_CLOSE_APP` 消息类型和 `CMD_CLOSE_APP` 命令名称定义。
 * - 因此，本Java类无需定义额外的字段，也不需要自定义Jackson序列化器/反序列化器。
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@SuperBuilder(toBuilder = true)
public class CloseAppCommand extends Command {

    @Override
    public MessageType getType() {
        return MessageType.CMD_CLOSE_APP;
    }

    @Override
    public CloseAppCommandBuilder<?, ?> cloneBuilder() {
        return (CloseAppCommandBuilder<?, ?>)super.cloneBuilder();
    }

    @Override
    protected CloseAppCommandBuilder<?, ?> innerToBuilder() {
        return toBuilder();
    }
}