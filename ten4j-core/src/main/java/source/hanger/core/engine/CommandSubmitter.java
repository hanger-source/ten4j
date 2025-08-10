package source.hanger.core.engine;

import source.hanger.core.message.CommandResult;
import source.hanger.core.message.command.Command;

import java.util.concurrent.CompletableFuture;

/**
 * `CommandSubmitter` 接口定义了将命令提交到 `Engine` 的能力。
 * 实现了此接口的类（例如 `Engine` 自身或 `ExtensionEnv`）能够发送命令，并接收其异步结果。
 * 这种模式使得命令的发送方无需关心命令的具体处理细节，只需提交命令并等待结果。
 */
public interface CommandSubmitter {
    /**
     * 提交一个命令。
     *
     * @param command 要提交的命令。
     * @return 返回一个 `CompletableFuture`，当命令处理完成时，该 Future 将被完成，包含命令执行的结果。
     *         如果命令处理失败，Future 将以异常方式完成。
     */
    CompletableFuture<CommandResult> submitCommand(Command command);

    /**
     * 提交一个命令结果。
     *
     * @param commandResult 要提交的命令结果。
     */
    void submitCommandResult(CommandResult commandResult);
}