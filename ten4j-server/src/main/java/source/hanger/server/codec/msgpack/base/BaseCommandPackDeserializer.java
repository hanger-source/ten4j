package source.hanger.server.codec.msgpack.base;

import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.Command.CommandBuilder;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public abstract class BaseCommandPackDeserializer<C extends Command, B extends CommandBuilder<? extends C, ?>> extends BaseMessagePackDeserializer<C, B> {
}
