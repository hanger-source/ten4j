package source.hanger.server.codec.msgpack.impl.deserializer;

import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.message.command.GenericCommand.GenericCommandBuilder;
import source.hanger.server.codec.msgpack.base.BaseCommandPackDeserializer;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public class GenericCommandPackDeserializer extends
    BaseCommandPackDeserializer<GenericCommand, GenericCommandBuilder<?, ?>> {

    @Override
    protected GenericCommandBuilder<? extends GenericCommand, ?> builder() {
        return GenericCommand.builder();
    }
}
