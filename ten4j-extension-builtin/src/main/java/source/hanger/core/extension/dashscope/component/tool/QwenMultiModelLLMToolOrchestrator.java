package source.hanger.core.extension.dashscope.component.tool;

import java.util.List;
import java.util.Map;

import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolCallFunction.CallFunction;
import com.alibaba.dashscope.tools.ToolFunction;

import source.hanger.core.extension.base.tool.LLMToolMetadata;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.extension.component.llm.LLMStreamAdapter;
import source.hanger.core.extension.component.llm.ToolCallOutputBlock;
import source.hanger.core.extension.component.tool.BaseLLMToolOrchestrator;
import source.hanger.core.message.MessageType;
import source.hanger.core.tenenv.TenEnv;

import static java.util.Collections.singletonList;
import static source.hanger.core.extension.dashscope.util.ToolConvertUtils.convertParametersToJsonObject;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public class QwenMultiModelLLMToolOrchestrator extends BaseLLMToolOrchestrator<MultiModalMessage, ToolFunction> {

    public QwenMultiModelLLMToolOrchestrator(LLMContextManager<MultiModalMessage> contextManager,
        LLMStreamAdapter<MultiModalMessage, ToolFunction> llmStreamAdapter) {
        super(contextManager, llmStreamAdapter);
    }

    @Override
    protected ToolFunction toToolFunction(LLMToolMetadata LLMToolMetadata) {
        FunctionDefinition functionDefinition = FunctionDefinition.builder()
            .name(LLMToolMetadata.getName())
            .description(LLMToolMetadata.getDescription())
            .parameters(convertParametersToJsonObject(LLMToolMetadata.getParameters()))
            .build();
        return ToolFunction.builder().function(functionDefinition).build();
    }

    @Override
    protected MultiModalMessage createToolCallAssistantMessage(ToolCallOutputBlock toolCallOutputBlock) {
        // 构建 Qwen 模型的 ToolCall Message (assistant 角色)
        // 将 tool_calls 信息添加到历史 (作为 assistant 角色)
        ToolCallFunction toolCallFunction = new ToolCallFunction();
        toolCallFunction.setId(toolCallOutputBlock.getToolCallId());
        CallFunction callFunction = toolCallFunction.new CallFunction();
        callFunction.setName(toolCallOutputBlock.getToolName());
        callFunction.setArguments(toolCallOutputBlock.getArgumentsJson());
        toolCallFunction.setFunction(callFunction);

        return MultiModalMessage.builder()
            .role(Role.ASSISTANT.getValue())
            .toolCalls(singletonList(toolCallFunction))
            .toolCallId(toolCallOutputBlock.getToolCallId())
            .build();
    }

    @Override
    protected MultiModalMessage createErrorToolCallMessage(ToolCallOutputBlock toolCallOutputBlock,
        Throwable cmdThrowable) {
        // 构建 Qwen 模型的错误 ToolCall Message (tool 角色)
        return MultiModalMessage.builder()
            .role(Role.TOOL.getValue())
            .toolCallId(toolCallOutputBlock.getToolCallId())
            .content(List.of(Map.of("text", "工具调用失败: %s".formatted(cmdThrowable.getMessage())))) // 错误信息作为 content
            .build();
    }

    @Override
    protected MultiModalMessage createToolCallMessage(ToolCallOutputBlock toolCallOutputBlock, String result) {
        // 构建 Qwen 模型的 ToolCall Message (tool 角色)
        return MultiModalMessage.builder()
            .role(Role.TOOL.getValue())
            .toolCallId(toolCallOutputBlock.getToolCallId())
            .content(List.of(Map.of("text", result)))
            .build();
    }

    @Override
    protected void sendErrorResult(TenEnv env, String commandId, MessageType type, String name, String errorMessage) {
        // 实现错误结果发送逻辑，如果需要的话
        // 目前 BaseLLMToolOrchestrator 中已经有默认的 sendErrorResult 方法，这里可以不实现
        // 或者根据 Qwen 的具体需求进行定制
    }

}
