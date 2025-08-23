package source.hanger.core.extension.component.tool;

import java.util.List;

import source.hanger.core.extension.base.tool.LLMToolMetadata;
import source.hanger.core.extension.component.llm.ToolCallOutputBlock;
import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;

import static java.util.Collections.*;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public class NoopLLMToolOrchestrator<T> implements LLMToolOrchestrator<T> {
    @Override
    public void registerTool(LLMToolMetadata LLMToolMetadata) {

    }

    @Override
    public List<T> getRegisteredToolFunctions() {
        return emptyList();
    }

    @Override
    public void processToolCall(TenEnv env, ToolCallOutputBlock toolCallOutputBlock, Message originalMessage) {

    }
}
