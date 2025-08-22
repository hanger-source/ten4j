package source.hanger.core.extension.dashscope.extension;

import java.util.function.Supplier;

import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.tools.ToolFunction;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseVisionExtension;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.extension.component.llm.LLMStreamAdapter;
import source.hanger.core.extension.component.tool.LLMToolOrchestrator;
import source.hanger.core.extension.dashscope.component.QwenMultiModalLLMContextManager;
import source.hanger.core.extension.dashscope.component.QwenMultiModalLLMStreamAdapter;
import source.hanger.core.extension.dashscope.component.QwenMultiModelLLMToolOrchestrator;

@Slf4j
public class QwenVisionExtension extends BaseVisionExtension<MultiModalMessage, ToolFunction> {

    @Override
    protected LLMContextManager<MultiModalMessage> createLLMContextManager(
        Supplier<String> systemPromptSupplier) {
        return new QwenMultiModalLLMContextManager(systemPromptSupplier);
    }

    @Override
    protected LLMStreamAdapter<MultiModalMessage, ToolFunction> createLLMStreamAdapter() {
        return new QwenMultiModalLLMStreamAdapter(extensionStateProvider, streamPipelineChannel);
    }

    @Override
    protected LLMToolOrchestrator<ToolFunction> createToolOrchestrator() {
        return new QwenMultiModelLLMToolOrchestrator(
            llmContextManager,
            llmStreamAdapter
        );
    }
}
