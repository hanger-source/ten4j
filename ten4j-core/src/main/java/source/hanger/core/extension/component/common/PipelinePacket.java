package source.hanger.core.extension.component.common;

import source.hanger.core.message.Message;

public record PipelinePacket<T>(T item, Message originalMessage) {
}
