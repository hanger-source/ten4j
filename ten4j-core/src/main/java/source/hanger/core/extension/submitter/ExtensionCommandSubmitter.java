package source.hanger.core.extension.submitter;

import source.hanger.core.message.CommandResult;
import source.hanger.core.message.command.Command;
import java.util.concurrent.CompletableFuture;

public interface ExtensionCommandSubmitter {
    CompletableFuture<CommandResult> submitCommandFromExtension(Command command, String sourceExtensionName);

    void routeCommandResultFromExtension(CommandResult commandResult, String sourceExtensionName); // New method
}