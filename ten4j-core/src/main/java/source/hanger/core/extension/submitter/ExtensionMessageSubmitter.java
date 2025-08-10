package source.hanger.core.extension.submitter;

import source.hanger.core.message.Message;

public interface ExtensionMessageSubmitter {
    void submitMessageFromExtension(Message message, String sourceExtensionName);
}