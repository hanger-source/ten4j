package com.tenframework.core.command;

import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.TimeoutCommand;
import com.tenframework.core.message.command.TimerCommand;
import com.tenframework.core.tenenv.TenEnv;

/**
 * `EngineCommandHandler` 接口定义了处理与 Engine 内部事件相关命令的方法。
 * 这是一个抽象接口，具体的命令处理器将实现它。
 * 它只处理 Engine 内部的命令，不包括 App 级别的命令（如 StartGraphCommand, StopGraphCommand,
 * CloseAppCommand）。
 */
public interface EngineCommandHandler {

    /**
     * 处理任意类型的命令。
     *
     * @param engineEnv Engine 的 TenEnv 实例。
     * @param command   要处理的命令。
     * @return 命令处理结果。
     */
    Object handle(TenEnv engineEnv, Command command);

    /**
     * 处理 `TimerCommand` 命令。
     *
     * @param engineEnv Engine 的 TenEnv 实例。
     * @param command   `TimerCommand` 命令对象。
     * @return 命令处理结果。
     */
    Object handleTimerCommand(TenEnv engineEnv, TimerCommand command);

    /**
     * 处理 `TimeoutCommand` 命令。
     *
     * @param engineEnv Engine 的 TenEnv 实例。
     * @param command   `TimeoutCommand` 命令对象。
     * @return 命令处理结果。
     */
    Object handleTimeoutCommand(TenEnv engineEnv, TimeoutCommand command);
}