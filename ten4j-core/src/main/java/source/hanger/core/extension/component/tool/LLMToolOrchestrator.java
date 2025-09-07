package source.hanger.core.extension.component.tool;

import java.util.List;

import source.hanger.core.extension.base.tool.LLMToolMetadata;
import source.hanger.core.extension.component.llm.ToolCallOutputBlock;
import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;

/**
 * 工具注册与调用协调器接口。
 * 负责工具的注册、管理、执行，以及将工具执行结果封装为 LLMToolCallOutputBlock 推送到主管道，
 * 并协调 LLM Agent 循环的继续。
 *
 * @param <TOOL_FUNCTION> LLM 工具函数定义的类型（如 DashScope 的 ToolCallFunction）。
 */
public interface LLMToolOrchestrator<TOOL_FUNCTION> {

    /**
     * 注册一个 LLM 工具。
     *
     * @param LLMToolMetadata toolMetadata
     */
    void registerTool(LLMToolMetadata LLMToolMetadata);

    /**
     * 获取所有已注册的工具函数列表，用于传递给 LLM。
     *
     * @return 已注册工具函数列表。
     */
    List<TOOL_FUNCTION> getRegisteredToolFunctions();

    /**
     * 处理 LLM 聚合完成的工具调用输出块。
     * 负责执行工具，并将执行结果添加到 LLM 历史中，并继续 LLM Agent 循环。
     *
     * @param env                 当前的 TenEnv 环境。
     * @param toolCallOutputBlock LLM 聚合完成的工具调用输出块。
     * @param originalMessage     触发此工具调用的原始消息。
     */
    void processToolCall(TenEnv env, ToolCallOutputBlock toolCallOutputBlock, Message originalMessage);

    default void triggerFlush(TenEnv env) {
    }
}
