package source.hanger.core.extension.base;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.tool.LLMTool;
import source.hanger.core.extension.base.tool.LLMToolResult;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;

import static source.hanger.core.common.ExtensionConstants.CMD_PROPERTY_RESULT;
import static source.hanger.core.common.ExtensionConstants.CMD_PROPERTY_TOOL;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL_PROPERTY_ARGUMENTS;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL_PROPERTY_NAME;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_REGISTER;
import static source.hanger.core.message.CommandResult.fail;
import static source.hanger.core.message.CommandResult.success;

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public abstract class BaseLLMToolExtension extends BaseExtension {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, LLMTool> tools;

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        tools = initTools(env).stream().collect(Collectors.toMap(LLMTool::getToolName, tool -> tool));
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        // 发送注册工具
        sendRegisterToolCommands(env);
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        // 处理 CMD_TOOL_CALL 命令
        if (CMD_TOOL_CALL.equals(command.getName())) {
            log.info("[{}] 收到 CMD_TOOL_CALL 命令，处理工具调用。", env.getExtensionName());
            handleToolCallCommand(env, command);
            return;
        }
    }

    private void sendRegisterToolCommands(TenEnv env) {
        // 从 BaseLLMToolExtension 迁移的逻辑：注册和发送工具元数据
        log.info("[{}] 工具扩展启动阶段", env.getExtensionName());
        if (tools != null && !tools.isEmpty()) {
            for (LLMTool tool : tools.values()) {

                try {
                    // 将工具元数据发送给LLM扩展
                    Command registerCmd = GenericCommand.create(CMD_TOOL_REGISTER);
                    String toolJson = objectMapper.writeValueAsString(tool.getToolMetadata());
                    registerCmd.setProperty(CMD_PROPERTY_TOOL, toolJson);
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

    public void handleToolCallCommand(TenEnv env, Command command) {
        String toolName = command.getProperty(CMD_TOOL_CALL_PROPERTY_NAME, String.class);
        if (toolName == null || !tools.containsKey(toolName)) {
            log.warn("[{}] 收到非本扩展的工具调用或工具名称为空，忽略。toolName={}", env.getExtensionName(), toolName);
            return;
        }
        try {
            String arguments = command.getPropertyString(CMD_TOOL_CALL_PROPERTY_ARGUMENTS).orElse("{}");
            Map<String, Object> args = objectMapper.readValue(arguments, new TypeReference<>() {});
            LLMToolResult toolResult = tools.get(toolName).runTool(env, command, args);

            String toolResultJson = objectMapper.writeValueAsString(toolResult);
            // 将工具结果放入 CMD_PROPERTY_RESULT 属性中
            Map<String, Object> properties = new java.util.HashMap<>();
            properties.put(CMD_PROPERTY_RESULT, toolResultJson);
            log.info("[{}] 工具 {} 执行并返回结果，发送成功命令。", env.getExtensionName(), toolName);
            env.sendResult(success(command, "Tool executed successfully.", properties)); // 使用带 properties 的重载
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
     * @param env 当前的 TenEnv 环境。
     * @return 此扩展提供的 LLMTool 实例列表。
     */
    protected abstract List<LLMTool> initTools(TenEnv env);
}
