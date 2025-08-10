package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 消息类型枚举，对齐C/Python中的TEN_MSG_TYPE。
 */
public enum MessageType {
    INVALID((byte) 0x00), // TEN_MSG_TYPE_INVALID
    CMD((byte) 0x01), // TEN_MSG_TYPE_CMD
    CMD_RESULT((byte) 0x02), // TEN_MSG_TYPE_CMD_RESULT
    CMD_CLOSE_APP((byte) 0x03), // TEN_MSG_TYPE_CMD_CLOSE_APP
    CMD_START_GRAPH((byte) 0x04), // TEN_MSG_TYPE_CMD_START_GRAPH
    CMD_STOP_GRAPH((byte) 0x05), // TEN_MSG_TYPE_CMD_STOP_GRAPH
    CMD_TIMER((byte) 0x06), // TEN_MSG_TYPE_CMD_TIMER
    CMD_TIMEOUT((byte) 0x07), // TEN_MSG_TYPE_CMD_TIMEOUT
    DATA((byte) 0x08), // TEN_MSG_TYPE_DATA
    VIDEO_FRAME((byte) 0x09), // TEN_MSG_TYPE_VIDEO_FRAME
    AUDIO_FRAME((byte) 0x0A); // TEN_MSG_TYPE_AUDIO_FRAME
    // TEN_MSG_TYPE_LAST 是一个占位符，不需要在Java枚举中表示

    private final byte value;

    MessageType(byte value) {
        this.value = value;
    }

    @JsonValue
    public byte getValue() {
        return value;
    }

    @JsonCreator
    public static MessageType fromValue(byte value) {
        for (MessageType type : MessageType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        // 对于未知的字节值，返回INVALID
        return INVALID;
    }
}