package source.hanger.core.message.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.message.MessageType;
import lombok.EqualsAndHashCode;

import static source.hanger.core.message.MessageType.CMD_TIMEOUT;

/**
 * 超时命令消息，对齐C/Python中的TEN_MSG_TYPE_CMD_TIMEOUT。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/cmd_base/cmd_timeout/cmd.h
 * (L24-27)
 * ```c
 * typedef struct ten_cmd_timeout_t {
 * ten_cmd_base_t cmd_base_hdr; // (基消息头)
 * ten_value_t timer_id; // int64 // 定时器ID
 * } ten_cmd_timeout_t;
 * ```
 *
 * Java 实现中，我们将 `timer_id` 直接作为类的字段，并通过 `@JsonProperty` 进行映射。
 * 不再需要自定义的 Jackson `JsonSerializer` 和 `JsonDeserializer`。
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@SuperBuilder(toBuilder = true)
@Getter
public class TimeoutCommand extends Command {

    /**
     * 定时器ID。
     * 对应C端 `ten_cmd_timeout_t` 结构体中的 `timer_id` 字段。
     */
    @JsonProperty("timer_id")
    private Long timerId;

    @Override
    public MessageType getType() {
        return CMD_TIMEOUT;
    }
}