package source.hanger.core.extension.component.llm;

import java.util.List;

import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;

/**
 * LLM 流服务接口。
 * 负责与 LLM 进行交互，处理原始流的解析、文本聚合、通用工具调用片段的生产，以及初步分发。
 *
 * @param <MESSAGE_TYPE>       LLM 对话消息的类型（如 DashScope 的 Message）。
 * @param <TOOL_FUNCTION_TYPE> LLM 工具函数定义的类型（如 DashScope 的 ToolCallFunction）。
 */
public interface LLMStreamAdapter<MESSAGE_TYPE, TOOL_FUNCTION_TYPE> {

    default void onStart(TenEnv env) {
    }
    /**
     * 发起 LLM 请求并处理其流式响应。
     * 此方法将负责订阅 LLM 的原始输出流，进行解析、聚合，并将处理后的逻辑块推送到主管道。
     *
     * @param env             当前的 TenEnv 环境。
     * @param messages        发送给 LLM 的消息列表（对话上下文）。
     * @param tools           提供给 LLM 的工具列表（如果支持工具调用）。
     * @param originalMessage 触发此 LLM 请求的原始消息。
     */
    void onRequestLLMAndProcessStream(TenEnv env, List<MESSAGE_TYPE> messages, List<TOOL_FUNCTION_TYPE> tools,
        Message originalMessage);

    /**
     * 取消当前 LLM 请求。
     * 通常在 flush 或其他中断操作时调用。
     *
     * @param env 当前的 TenEnv 环境。
     */
    void onCancelLLM(TenEnv env);
}
