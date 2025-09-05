package source.hanger.core.message.command;

import lombok.experimental.SuperBuilder;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;

import static source.hanger.core.message.MessageType.*;

@SuperBuilder(toBuilder = true)
public class GenericCommand extends Command {

    public static GenericCommand create(String name) {
        return create(name, null);
    }

    public static GenericCommand create(String name, String originalCommandId) {
        return createBuilder(name, originalCommandId)
            .build();
    }

    public static GenericCommandBuilder<?,?> createBuilder(String name) {
        return createBuilder(name, null);
    }

    public static GenericCommandBuilder<?,?> createBuilder(String name, String originalCommandId) {
        return Message.defaultMessage(GenericCommand.builder())
            .name(name)
            .parentCommandId(originalCommandId);
    }

    @Override
    public MessageType getType() {
        return CMD;
    }
}