package com.tenframework.core.command.engine;

import com.tenframework.core.command.EngineCommandHandler;
import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.message.command.TimeoutCommand;
import com.tenframework.core.message.command.TimerCommand;
import com.tenframework.core.tenenv.TenEnv;
import lombok.extern.slf4j.Slf4j;

/**
 * `TimerCommandHandler` 处理 `TimerCommand` 命令。
 * 这是一个 Engine 内部的命令处理器，用于处理定时器触发事件。
 */
@Slf4j
public class TimerCommandHandler implements EngineCommandHandler {

    @Override
    public Object handle(TenEnv engineEnv, Command command) {
        if (!(command instanceof TimerCommand)) {
            log.warn("TimerCommandHandler 收到非 TimerCommand 命令: {}", command.getType());
            // 返回失败结果
            return CommandResult.fail(command.getId(), "Unexpected command type for TimerHandler.");
        }
        return handleTimerCommand(engineEnv, (TimerCommand) command);
    }

    @Override
    public Object handleTimerCommand(TenEnv engineEnv, TimerCommand command) {
        log.debug("TimerCommand received for timerId: {}, timeoutUs: {}, times: {}",
                command.getTimerId(), command.getTimeoutUs(), command.getTimes());
        // TODO: Implement timer logic
        return "Timer " + command.getTimerId() + " handled.";
    }

    @Override
    public Object handleTimeoutCommand(TenEnv engineEnv, TimeoutCommand command) {
        // TimerCommandHandler 不处理 TimeoutCommand
        log.warn("TimerCommandHandler 不支持 TimeoutCommand: {}", command.getId());
        return CommandResult.fail(command.getId(),
                "Not supported: TimerCommandHandler does not handle TimeoutCommand.");
    }
}