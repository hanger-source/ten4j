package source.hanger.core.extension.submitter;

import source.hanger.core.message.CommandResult;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.RunloopFuture;

public interface ExtensionCommandSubmitter {
    RunloopFuture<CommandResult> submitCommandFromExtension(Command command, String sourceExtensionName);

    void routeCommandResultFromExtension(CommandResult commandResult, String sourceExtensionName);
}