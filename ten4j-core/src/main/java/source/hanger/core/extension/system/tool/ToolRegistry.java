package source.hanger.core.extension.system.tool;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {
    private static final ToolRegistry INSTANCE = new ToolRegistry();
    private final Map<String, LLMTool> registeredTools = new ConcurrentHashMap<>();

    private ToolRegistry() {
    }

    public static ToolRegistry getInstance() {
        return INSTANCE;
    }

    public void registerTool(LLMTool tool) {
        registeredTools.put(tool.getToolName(), tool);
    }

    public LLMTool getTool(String toolName) {
        return registeredTools.get(toolName);
    }

    public Map<String, LLMTool> getAllTools() {
        return Collections.unmodifiableMap(registeredTools);
    }
}
