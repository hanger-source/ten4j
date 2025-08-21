package source.hanger.core.extension.dashscope.component;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolFunction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import source.hanger.core.extension.api.tool.LLMTool;
import source.hanger.core.extension.api.tool.ToolMetadata;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.extension.component.llm.LLMStreamAdapter;
import source.hanger.core.extension.component.llm.ToolCallOutputBlock;
import source.hanger.core.extension.component.tool.BaseLLMToolOrchestrator;
import source.hanger.core.message.MessageType;
import source.hanger.core.tenenv.TenEnv;

import static source.hanger.core.extension.api.tool.ToolMetadata.ToolParameter;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public class QwenChatLLMToolOrchestrator extends BaseLLMToolOrchestrator<GenerationResult, Message, ToolFunction> {

    public QwenChatLLMToolOrchestrator(LLMContextManager<Message> contextManager,
        LLMStreamAdapter<Message, ToolFunction> llmStreamAdapter) {
        super(contextManager, llmStreamAdapter);
    }

    @Override
    protected ToolFunction toToolFunction(LLMTool tool) {
        if (tool == null || tool.getToolMetadata() == null || tool.getToolMetadata().getName() == null) {
            return null;
        }

        ToolMetadata toolMetadata = tool.getToolMetadata();
        FunctionDefinition functionDefinition = FunctionDefinition.builder()
            .name(toolMetadata.getName())
            .description(toolMetadata.getDescription())
            .parameters(convertParametersToJsonObject(toolMetadata.getParameters()))
            .build();
        return ToolFunction.builder().function(functionDefinition).build();
    }

    @Override
    protected Message createToolCallAssistantMessage(ToolCallOutputBlock toolCallOutputBlock) {
        return null;
    }

    @Override
    protected Message createErrorToolCallMessage(ToolCallOutputBlock toolCallOutputBlock, Throwable cmdThrowable) {
        return null;
    }

    @Override
    protected Message createToolCallMessage(ToolCallOutputBlock toolCallOutputBlock, String result) {
        return null;
    }

    @Override
    protected void sendErrorResult(TenEnv env, String commandId, MessageType type, String name, String errorMessage) {

    }

    // 辅助方法：将 List<ToolParameter> 转换为 JsonObject (此处沿用旧 BaseLLMToolExtension 的逻辑)
    private JsonObject convertParametersToJsonObject(List<ToolParameter> parameters) {
        JsonObject schemaObject = new JsonObject();
        schemaObject.addProperty("type", "object");

        JsonObject propertiesObject = new JsonObject();
        List<String> requiredList = new ArrayList<>();

        if (parameters != null) {
            for (ToolParameter param : parameters) {
                JsonObject paramProps = new JsonObject();
                paramProps.addProperty("type", param.getType());
                paramProps.addProperty("description", param.getDescription());
                propertiesObject.add(param.getName(), paramProps);
                if (param.isRequired()) {
                    requiredList.add(param.getName());
                }
            }
        }
        schemaObject.add("properties", propertiesObject);

        if (!requiredList.isEmpty()) {
            JsonArray jsonArray = new JsonArray();
            for (String requiredParam : requiredList) {
                jsonArray.add(requiredParam);
            }
            schemaObject.add("required", jsonArray);
        }
        return schemaObject;
    }

}
