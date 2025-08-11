package source.hanger.core.message;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 消息类型枚举，对齐C/Python中的TEN_MSG_TYPE。
 */
public enum MessageType {
    INVALID,
    CMD,
    CMD_RESULT,
    CMD_CLOSE_APP,
    CMD_START_GRAPH,
    CMD_STOP_GRAPH,
    CMD_TIMER,
    CMD_TIMEOUT,
    DATA,
    VIDEO_FRAME,
    AUDIO_FRAME,
    // TEN_MSG_TYPE_LAST 是一个占位符，不需要在Java枚举中表示

    ;

    @JsonCreator
    public static MessageType fromString(String name) {
        for (MessageType type : MessageType.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return INVALID;
    }
}