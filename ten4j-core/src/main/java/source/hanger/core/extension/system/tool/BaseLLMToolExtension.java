package source.hanger.core.extension.system.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.common.ExtensionConstants;
import source.hanger.core.extension.BaseExtension;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;

import java.util.List;
import java.util.Map;

@Slf4j
public abstract class BaseLLMToolExtension extends BaseExtension {

    protected LLMToolService llmToolService;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    public BaseLLMToolExtension() {
        super();
    }

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        super.onConfigure(env, properties);
        llmToolService = new LLMToolService(env);
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[{}] 工具扩展启动阶段", env.getExtensionName());
        List<LLMTool> tools = getTools(env);
        if (tools != null && !tools.isEmpty()) {
            for (LLMTool tool : tools) {
                ToolRegistry.getInstance().registerTool(tool);
                try {
                    // 将工具元数据发送给LLM扩展
                    Command registerCmd = GenericCommand.create(ExtensionConstants.CMD_TOOL_REGISTER);
                    String toolJson = objectMapper.writeValueAsString(tool.getToolMetadata());
                    registerCmd.setProperty(ExtensionConstants.CMD_PROPERTY_TOOL, toolJson);
                    env.sendCmd(registerCmd);
                    log.info("[{}] 工具注册命令发送成功: toolName={}",env.getExtensionName(), tool.getToolName());
                } catch (Exception e) {
                    log.error("[{}] 发送工具注册命令失败: toolName={}, error={}",env.getExtensionName(), tool.getToolName(), e.getMessage(), e);
                }
            }
        } else {
            log.warn("[{}] 工具扩展未注册任何工具", env.getExtensionName());
        }
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        if (!isRunning()) {
            log.warn("[{}] 工具扩展未运行，忽略命令:commandName={}",
                env.getExtensionName(), command.getName());
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            String commandName = command.getName();
            if (ExtensionConstants.CMD_TOOL_CALL.equals(commandName)) {
                try {
                    LLMToolResult toolResult = llmToolService.handleToolCallCommand(command);
                    String toolResultJson = objectMapper.writeValueAsString(toolResult);

                    // 将工具结果放入 CMD_PROPERTY_RESULT 属性中
                    Map<String, Object> properties = new java.util.HashMap<>();
                    properties.put(ExtensionConstants.CMD_PROPERTY_RESULT, toolResultJson);

                    log.info("[{}] 工具 {} 执行并返回结果，发送成功命令。",env.getExtensionName(), command.getProperty("name"));
                    env.sendResult(CommandResult.success(command, "Tool executed successfully.", properties)); // 使用带 properties 的重载
                } catch (Exception e) {
                    String errorMessage = "工具执行失败: %s".formatted(e.getMessage());
                    log.error("[{}] 工具 {} 执行失败，发送失败命令: {}",env.getExtensionName(), command.getProperty("name"), errorMessage, e);
                    env.sendResult(CommandResult.fail(command, errorMessage));
                }
            } else {
                super.onCmd(env, command);
            }
            long duration = System.currentTimeMillis() - startTime;
            log.debug("[{}] 工具扩展命令 {} 处理耗时: {} ms",env.getExtensionName(), commandName, duration);
        } catch (Exception e) {
            log.error("[{}] 工具扩展命令处理异常:commandName={}",
                env.getExtensionName(), command.getName(), e);
            CommandResult errorResult = CommandResult.fail(command, "工具扩展命令处理异常: %s".formatted(e.getMessage()));
            env.sendResult(errorResult);
        }
    }

    protected abstract List<LLMTool> getTools(TenEnv env);
}
