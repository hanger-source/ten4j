package source.hanger.core.extension.component.tool;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.tool.LLMTool;
import source.hanger.core.extension.base.tool.ToolCallPayload;
import source.hanger.core.extension.base.tool.ToolCallPayload.ErrorPayload;
import source.hanger.core.extension.base.tool.ToolCallPayload.FinalPayload;
import source.hanger.core.extension.base.tool.ToolCallPayload.SegmentPayload;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;

import static source.hanger.core.common.ExtensionConstants.CMD_PROPERTY_TOOL;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL_PROPERTY_ARGUMENTS;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL_PROPERTY_NAME;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_PROPERTY_SECOND_ROUND;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_PROPERTY_TOOL_CALL_CONTENT;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_PROPERTY_ASSISTANT_MESSAGE;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_REGISTER;
import static source.hanger.core.message.CommandResult.fail;
import static source.hanger.core.message.CommandResult.invalid;
import static source.hanger.core.message.CommandResult.success;

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public abstract class ExtensionToolDelegate {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, LLMTool> tools;

    public ExtensionToolDelegate() {
        tools = initTools().stream().collect(Collectors.toMap(LLMTool::getToolName, tool -> tool));
    }

    public void sendRegisterToolCommands(TenEnv env) {
        // 从 BaseLLMToolExtension 迁移的逻辑：注册和发送工具元数据
        log.info("[{}] 工具扩展启动阶段", env.getExtensionName());
        if (tools != null && !tools.isEmpty()) {
            for (LLMTool tool : tools.values()) {

                try {
                    // 将工具元数据发送给LLM扩展
                    String toolJson = objectMapper.writeValueAsString(tool.getToolMetadata());
                    Command registerCmd = GenericCommand.createBuilder(CMD_TOOL_REGISTER)
                        .property(CMD_PROPERTY_TOOL, toolJson)
                        .build();

                    env.sendCmd(registerCmd);
                    log.info("[{}] 工具注册命令发送成功: toolName={}", env.getExtensionName(), tool.getToolName());
                } catch (Exception e) {
                    log.error("[{}] 发送工具注册命令失败: toolName={}, error={}", env.getExtensionName(),
                        tool.getToolName(), e.getMessage(), e);
                }
            }
        } else {
            log.warn("[{}] 工具扩展未注册任何工具", env.getExtensionName());
        }
    }

    public ToolCallPayloadEmitter emitter(TenEnv tenEnv, Command command) {
        return payloadBuilder -> {
            ToolCallPayload payload = payloadBuilder.build();
            if (payload instanceof FinalPayload finalPayload) {
                tenEnv.sendResult(CommandResult.createSuccessBuilder(command)
                        .property(CMD_TOOL_PROPERTY_TOOL_CALL_CONTENT, finalPayload.getToolCallContext())
                        .property(CMD_TOOL_PROPERTY_ASSISTANT_MESSAGE, finalPayload.getAssistantMessage())
                        .property(CMD_TOOL_PROPERTY_SECOND_ROUND, finalPayload.getSecondRound())
                    .build());
            } else if (payload instanceof ErrorPayload errorPayload) {
                tenEnv.sendResult(CommandResult.createErrorBuilder(command)
                        .property(CMD_TOOL_PROPERTY_TOOL_CALL_CONTENT, errorPayload.getToolCallContext())
                        .property(CMD_TOOL_PROPERTY_SECOND_ROUND, errorPayload.getSecondRound())
                    .build());
            } else if (payload instanceof SegmentPayload progressUpdate) {
                tenEnv.sendResult(CommandResult.createSuccessBuilder(command)
                        .isFinal(false)
                        .isCompleted(false)
                        .property(CMD_TOOL_PROPERTY_TOOL_CALL_CONTENT, progressUpdate.getToolCallContext())
                        .property(CMD_TOOL_PROPERTY_ASSISTANT_MESSAGE, progressUpdate.getAssistantMessage())
                        .property(CMD_TOOL_PROPERTY_SECOND_ROUND, progressUpdate.getSecondRound())
                    .build());
            }
        };
    }

    public void handleToolCallCommand(TenEnv env, Command command) {
        String toolName = command.getPropertyString(CMD_TOOL_CALL_PROPERTY_NAME).orElse("");
        if (!tools.containsKey(toolName)) {
            env.sendResult(invalid(command, "收到非本扩展的工具调用或工具名称为空"));
            log.warn("[{}] 收到非本扩展的工具调用或工具名称为空，忽略。toolName={}", env.getExtensionName(), toolName);
            return;
        }
        try {
            String arguments = command.getPropertyString(CMD_TOOL_CALL_PROPERTY_ARGUMENTS).orElse("{}");
            Map<String, Object> args = objectMapper.readValue(arguments, new TypeReference<>() {});
            tools.get(toolName).runTool(emitter(env, command), env, command, args);

        } catch (Exception e) {
            String errorMessage = "工具执行失败: %s".formatted(e.getMessage());
            log.error("[{}] 工具 {} 执行失败，发送失败命令: {}", env.getExtensionName(), toolName, errorMessage, e);
            env.sendResult(fail(command, errorMessage));
        }
    }

    /**
     * 抽象方法：获取此扩展提供的 LLM 工具列表。
     * 子类应实现此方法以返回其支持的 LLMTool 实例列表。
     *
     * @return 此扩展提供的 LLMTool 实例列表。
     */
    public abstract List<LLMTool> initTools();
}
