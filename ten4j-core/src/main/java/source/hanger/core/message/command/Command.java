package source.hanger.core.message.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.message.Message;

/**
 * 所有命令消息的抽象基类，继承自 Message。
 * 提供命令特有的基本属性和 Jackson 多态序列化/反序列化配置。
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@Slf4j
@SuperBuilder(toBuilder = true)
public abstract class Command extends Message {

    @JsonProperty("parent_cmd_id")
    protected String parentCommandId; // 新增：父命令ID，对齐C端

    @Override
    public CommandBuilder<?, ?> cloneBuilder() {
        return (CommandBuilder<?, ?>)super.cloneBuilder();
    }

    @Override
    protected abstract CommandBuilder<?, ?> innerToBuilder();
}