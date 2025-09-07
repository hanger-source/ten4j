package source.hanger.core.engine;

import source.hanger.core.message.CommandExecutionHandle;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.command.Command;

/**
 * 命令提交器接口，定义了提交命令的方法。
 * Engine, App, ExtensionContext 都将实现此接口，以便在不同的上下文提交命令。
 * 参照 C 语言 ten_env_send_cmd 的行为，提供了“即发即弃”和“期望结果句柄”两种模式。
 */
public interface CommandSubmitter {

    /**
     * 提交一个命令，并返回一个 CommandExecutionHandle 以便异步处理其结果。
     * 适用于需要追踪命令执行状态和接收回调的场景。
     * 底层将创建 PathOut 来追踪命令的生命周期和结果，并可能将其组织到 PathGroup 中。
     *
     * @param command 要提交的命令。
     * @return 用于管理命令结果的 CommandExecutionHandle。
     */
    CommandExecutionHandle<CommandResult> submitCommandWithResultHandle(Command command);

    /**
     * 提交一个命令结果。
     *
     * @param commandResult 要提交的命令结果。
     */
    void submitCommandResult(CommandResult commandResult);
}