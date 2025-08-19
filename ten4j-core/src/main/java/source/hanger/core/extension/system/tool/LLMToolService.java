package source.hanger.core.extension.system.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.common.ExtensionConstants;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.message.command.Command;

import java.util.Map;

@Slf4j
public class LLMToolService {
    private final TenEnv env;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LLMToolService(TenEnv env) {
        this.env = env;
    }

    public LLMToolResult handleToolCallCommand(Command command) throws Exception {
        try {
            String toolName = command.getProperty("name", String.class);
            String toolArgsJson = command.getProperty("arguments", String.class);
            Map<String, Object> toolArgs = objectMapper.readValue(toolArgsJson, Map.class);

            LLMTool tool = ToolRegistry.getInstance().getTool(toolName);
            if (tool == null) {
                String errorMessage = String.format("[LLM_REFACTOR] 未找到工具: %s (可能未注册到ToolRegistry)", toolName);
                log.error(errorMessage);
                throw new RuntimeException(errorMessage); // 抛出运行时异常，由调用方捕获并处理为CommandResult.fail
            }

            LLMToolResult result = tool.runTool(env, toolArgs);
            if (result != null) {
                log.info("[LLM_REFACTOR] 工具 {} 执行成功", toolName);
                return result;
            } else {
                log.warn("[LLM_REFACTOR] 工具 {} 执行但未返回结果，将返回空结果给LLM", toolName);
                return new LLMToolResult.LLMResult(""); // 返回空LLMResult，表示成功但无内容
            }
        } catch (Exception e) {
            String errorMessage = String.format("[LLM_REFACTOR] 处理工具调用命令失败: %s", e.getMessage());
            log.error(errorMessage, e);
            throw e; // 将异常重新抛出，由调用方负责处理
        }
    }

    public boolean isLLMToolCallCommand(Command command) {
        return ExtensionConstants.CMD_TOOL_CALL.equals(command.getName());
    }
}
