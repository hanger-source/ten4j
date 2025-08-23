package source.hanger.core.extension.dashscope.extension;

import java.util.function.Supplier;

import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.tools.ToolFunction;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseLLMExtension;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.extension.component.llm.LLMStreamAdapter;
import source.hanger.core.extension.component.tool.LLMToolOrchestrator;
import source.hanger.core.extension.dashscope.component.context.QwenChatLLMContextManager;
import source.hanger.core.extension.dashscope.component.stream.QwenChatLLMStreamAdapter;
import source.hanger.core.extension.dashscope.component.tool.QwenChatLLMToolOrchestrator;
import source.hanger.core.tenenv.TenEnv;

/**
 *
 */
@Slf4j
public class QwenChatLlmExtension
    extends BaseLLMExtension<Message, ToolFunction> {

    @Override
    protected LLMContextManager<Message> createLLMContextManager(TenEnv env, Supplier<String> systemPromptSupplier) {
        return new QwenChatLLMContextManager(env, systemPromptSupplier);
    }

    @Override
    protected LLMStreamAdapter<Message, ToolFunction> createLLMStreamAdapter() {
        return new QwenChatLLMStreamAdapter(
            extensionStateProvider,
            streamPipelineChannel
        );
    }

    @Override
    protected LLMToolOrchestrator<ToolFunction> createToolOrchestrator() {
        return new QwenChatLLMToolOrchestrator(
            llmContextManager,
            llmStreamAdapter // 传入 LLMStreamService 实例
        );
    }

}
