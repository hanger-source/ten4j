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
 * `TimeoutCommandHandler` 处理 `TimeoutCommand` 命令。
 * 这是一个 Engine 内部的命令处理器，用于处理定时器超时事件。
 */
@Slf4j
public class TimeoutCommandHandler implements EngineCommandHandler {

    @Override
    public Object handle(TenEnv engineEnv, Command command) {
        if (!(command instanceof TimeoutCommand)) {
            log.warn("TimeoutCommandHandler 收到非 TimeoutCommand 命令: {}", command.getType());
            // 返回失败结果
            return CommandResult.fail(command.getId(), "Unexpected command type for TimeoutHandler.");
        }
        return handleTimeoutCommand(engineEnv, (TimeoutCommand) command);
    }

    @Override
    public Object handleTimerCommand(TenEnv engineEnv, TimerCommand command) {
        // TimeoutCommandHandler 不处理 TimerCommand
        log.warn("TimeoutCommandHandler 不支持 TimerCommand: {}", command.getId());
        return CommandResult.fail(command.getId(),
                "Not supported: TimeoutCommandHandler does not handle TimerCommand.");
    }

    @Override
    public Object handleTimeoutCommand(TenEnv engineEnv, TimeoutCommand command) {
        log.info("Engine {}: 收到 TimeoutCommand: Command ID={}, Data={}",
                engineEnv.getGraphId(), command.getId(), command.getProperties());
        // TODO: 根据实际业务需求处理 TimeoutCommand，例如触发某个业务逻辑
        // 假设这里只是简单返回成功
        return CommandResult.success(command.getId(), "Timeout command processed successfully.");
    }
}