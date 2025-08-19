package source.hanger.core.extension.system.tool;

import java.util.Map;

import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

public interface LLMTool {
    ToolMetadata getToolMetadata();

    LLMToolResult runTool(TenEnv env, Command command, Map<String, Object> args);
    String getToolName();
}
