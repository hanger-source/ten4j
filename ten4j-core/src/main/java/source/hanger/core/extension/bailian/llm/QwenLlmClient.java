package source.hanger.core.extension.bailian.llm;

import java.util.List;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云通义千问 LLM 客户端。
 * 负责与 DashScope SDK 交互，进行聊天补全。
 */
@Slf4j
public class QwenLlmClient {

    private final Generation generation;
    private final String apiKey;
    private final String model;
    // private volatile Disposable currentDisposable = Disposables.empty(); //
    // 用于管理当前活跃的请求

    /**
     * 构造函数。
     *
     * @param apiKey DashScope API Key
     * @param model  使用的模型名称（例如 "qwen-plus"）
     */
    public QwenLlmClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.generation = new Generation();
    }

    /**
     * 以流式模式调用聊天补全API。
     *
     * @param messages 用户消息列表 (现在接收 List<Message>)
     * @return 返回 LLM 生成的原始结果流
     */
    public Flowable<GenerationResult> streamChatCompletion(List<Message> messages) {
        // 在开始新的请求前，先取消之前的请求 (现在由 Extension 的 Flowable 订阅取消机制处理)
        // cancelCurrentRequest();

        GenerationParam param = GenerationParam.builder()
            .apiKey(apiKey)
            .model(model)
            .messages(messages)
            .resultFormat(GenerationParam.ResultFormat.MESSAGE)
            .incrementalOutput(true)
            .build();

        Flowable<GenerationResult> resultFlowable;
        try {
            resultFlowable = generation.streamCall(param);
        } catch (NoApiKeyException | InputRequiredException e) {
            return Flowable.error(e);
        }

        // 保存当前的订阅，以便后续取消 (现在由 Extension 的 Flowable 订阅管理)
        // currentDisposable = resultFlowable.subscribe();

        return resultFlowable;
    }

    // /**
    // * 取消当前正在进行的 LLM 请求。
    // */
    // public void cancelCurrentRequest() {
    // if (currentDisposable != null && !currentDisposable.isDisposed()) {
    // log.info("Cancelling current Qwen LLM request.");
    // currentDisposable.dispose();
    // currentDisposable = Disposables.empty();
    // }
    // }
}
