package source.hanger.core.extension.system.tool;

import java.util.Map;

import source.hanger.core.tenenv.TenEnv;

public interface LLMTool {
    ToolMetadata getToolMetadata();
    LLMToolResult runTool(TenEnv env, Map<String, Object> args);
    String getToolName();
}
