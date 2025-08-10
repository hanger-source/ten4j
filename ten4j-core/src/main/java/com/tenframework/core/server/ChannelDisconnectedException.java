package com.tenframework.core.server;

/**
 * 当Netty Channel断开连接时抛出的异常，用于通知相关Command无法返回结果。
 */
public class ChannelDisconnectedException extends RuntimeException {

    public ChannelDisconnectedException(String message) {
        super(message);
    }

    public ChannelDisconnectedException(String message, Throwable cause) {
        super(message, cause);
    }
}