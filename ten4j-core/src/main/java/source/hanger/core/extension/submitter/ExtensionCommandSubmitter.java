package source.hanger.core.extension.submitter;

import source.hanger.core.message.CommandResult;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.CommandExecutionHandle;

public interface ExtensionCommandSubmitter {
    CommandExecutionHandle<CommandResult> submitCommandFromExtension(Command command, String sourceExtensionName);

    void submitCommandResultFromExtension(CommandResult commandResult, String sourceExtensionName);
}