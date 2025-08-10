package com.tenframework.core.app;

import com.tenframework.core.message.command.Command;

/**
 * `CommandReceiver` 接口定义了接收框架内命令的契约。
 * App 和 Engine 都可以实现此接口来处理传入的命令。
 */
public interface CommandReceiver {

    /**
     * 处理传入的命令消息。
     *
     * @param command 传入的命令消息。
     * @return 命令处理结果的 CompletableFuture 或其他适当的响应对象。
     */
    Object handleCommand(Command command);
}