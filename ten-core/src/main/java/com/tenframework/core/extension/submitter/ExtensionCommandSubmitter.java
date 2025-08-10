package com.tenframework.core.extension.submitter;

import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.command.Command;
import java.util.concurrent.CompletableFuture;

public interface ExtensionCommandSubmitter {
    CompletableFuture<CommandResult> submitCommandFromExtension(Command command, String sourceExtensionName);

    void routeCommandResultFromExtension(CommandResult commandResult, String sourceExtensionName); // New method
}