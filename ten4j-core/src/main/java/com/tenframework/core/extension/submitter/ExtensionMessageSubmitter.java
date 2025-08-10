package com.tenframework.core.extension.submitter;

import com.tenframework.core.message.Message;

public interface ExtensionMessageSubmitter {
    void submitMessageFromExtension(Message message, String sourceExtensionName);
}