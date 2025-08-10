package com.tenframework.core.message;

public final class MessageConstants {
    // 自定义 MsgPack 扩展类型
    public static final byte TEN_MSGPACK_EXT_TYPE_MSG = (byte) -1; // 统一为 -1，与 C/Python 和前端对齐

    private MessageConstants() {
        // 私有构造函数，防止实例化
    }
}