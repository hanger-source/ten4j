package source.hanger.server.codec.msgpack;

import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

public interface MessagePackDeserializer<R> {
    R deserialize(MessageUnpacker unpacker) throws IOException;
}
