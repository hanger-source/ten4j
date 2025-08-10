package com.tenframework.core.util;

import java.util.UUID;

/**
 * 消息工具类，提供生成唯一ID等实用方法。
 */
public class MessageUtils {

    /**
     * 生成一个随机的唯一ID。
     *
     * @return 唯一ID字符串。
     */
    public static String generateUniqueId() {
        return UUID.randomUUID().toString();
    }
}