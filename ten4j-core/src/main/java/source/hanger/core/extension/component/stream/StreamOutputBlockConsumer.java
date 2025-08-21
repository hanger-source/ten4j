package source.hanger.core.extension.component.stream;

import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;

/**
 * 处理流中单个 LLM 输出项的接口。
 * 实现此接口的类将负责消费 StreamPipelineManager 发出的 LLMOutputBlock。
 */
public interface StreamOutputBlockConsumer<T extends OutputBlock> {
    /**
     * 处理从 StreamPipelineManager 接收到的单个 LLM 输出项。
     *
     * @param item            要处理的 LLMOutputBlock。
     * @param originalMessage 触发此 LLM 响应的原始消息。
     * @param env             当前的 TenEnv 环境。
     */
    void consumeOutputBlock(T item, Message originalMessage, TenEnv env);
}
