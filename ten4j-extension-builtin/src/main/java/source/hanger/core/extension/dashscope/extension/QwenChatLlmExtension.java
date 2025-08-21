package source.hanger.core.extension.dashscope.extension;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.tools.ToolFunction;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.api.BaseLLMExtension;
import source.hanger.core.extension.api.tool.LLMTool;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.extension.component.llm.LLMStreamAdapter;
import source.hanger.core.extension.component.tool.LLMToolOrchestrator;
import source.hanger.core.extension.dashscope.component.DashScopeLLMContextManager;
import source.hanger.core.extension.dashscope.component.QwenChatLLMStreamTransformer;
import source.hanger.core.extension.dashscope.component.QwenChatLLMToolOrchestrator;
import source.hanger.core.tenenv.TenEnv;

/**
 *
 */
@Slf4j
public class QwenChatLlmExtension
    extends BaseLLMExtension<Message, ToolFunction> {

    @Override
    protected LLMContextManager<Message> createLLMContextManager(
        Supplier<String> systemPromptSupplier) {
        return new DashScopeLLMContextManager(systemPromptSupplier);
    }

    @Override
    protected List<LLMTool> getTools(TenEnv env) {
        return Collections.emptyList();
    }

    @Override
    protected LLMStreamAdapter<Message, ToolFunction> createLLMStreamTransformer() {
        return new QwenChatLLMStreamTransformer(
            interruptionStateProvider,
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
