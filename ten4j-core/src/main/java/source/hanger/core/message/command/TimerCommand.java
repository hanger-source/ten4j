package source.hanger.core.message.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.message.MessageType;
import lombok.EqualsAndHashCode;

/**
 * 定时器命令消息，对齐C/Python中的TEN_MSG_TYPE_CMD_TIMER。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/cmd_base/cmd_timer/cmd.h
 * (L24-31)
 * ```c
 * typedef struct ten_cmd_timer_t {
 * ten_cmd_base_t cmd_base_hdr; // (基消息头)
 * ten_value_t timer_id; // int64 // 定时器ID
 * ten_value_t timeout_us; // int64 // 超时时间（微秒）
 * ten_value_t times; // int32 // 重复次数
 * } ten_cmd_timer_t;
 * ```
 *
 * Java 实现中，我们将这些字段直接作为类的字段，并通过 `@JsonProperty` 进行映射。
 * 不再需要自定义的 Jackson `JsonSerializer` 和 `JsonDeserializer`。
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@SuperBuilder(toBuilder = true)
@Getter
public class TimerCommand extends Command {

    /**
     * 定时器ID。
     * 对应C端 `ten_cmd_timer_t` 结构体中的 `timer_id` 字段。
     */
    @JsonProperty("timer_id")
    private Long timerId;

    /**
     * 超时时间（微秒）。
     * 对应C端 `ten_cmd_timer_t` 结构体中的 `timeout_us` 字段。
     */
    @JsonProperty("timeout_us")
    private Long timeoutUs;

    /**
     * 重复次数。
     * 对应C端 `ten_cmd_timer_t` 结构体中的 `times` 字段。
     */
    @JsonProperty("times")
    private Integer times; // -1 表示无限循环

    @Override
    public MessageType getType() {
        return MessageType.CMD_TIMER;
    }
}