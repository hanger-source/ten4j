package source.hanger.core.extension.component.tool;

import java.util.List;

import source.hanger.core.extension.base.tool.LLMTool;
import source.hanger.core.tenenv.TenEnv;

/**
 * 工具提供者接口。
 * 负责提供 Extension 可用的 LLM 工具列表。
 *
 * @param <TOOL_TYPE> LLM 工具的具体类型，必须继承自 LLMTool。
 */
public interface ToolProvider<TOOL_TYPE extends LLMTool> {

    /**
     * 获取此 Extension 提供的 LLM 工具列表。
     *
     * @param env 当前的 TenEnv 环境，可能用于工具的初始化或配置。
     * @return 此 Extension 支持的 LLM 工具列表。
     */
    List<TOOL_TYPE> getTools(TenEnv env);
}
