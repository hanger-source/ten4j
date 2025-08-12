package source.hanger.core.extension.qwen.llm;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.JsonUtils;

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
     * @param callback 用于处理流式输出的回调函数
     */
    public void streamChatCompletion(List<Message> messages, QwenLlmStreamCallback callback) {
        GenerationParam param = GenerationParam.builder()
            .apiKey(apiKey)
            .model(model)
            .messages(messages)
            .resultFormat(GenerationParam.ResultFormat.MESSAGE)
            .incrementalOutput(true)
            .build();

        try {
            Flowable<GenerationResult> resultFlowable = generation.streamCall(param);
            AtomicReference<String> accumulatedContent = new AtomicReference<>("");

            resultFlowable.blockingForEach(message -> {
                if (message != null && message.getOutput() != null && message.getOutput().getChoices() != null
                    && !message.getOutput().getChoices().isEmpty()) {
                    GenerationResult result = message;
                    String content = result.getOutput().getChoices().get(0).getMessage().getContent();
                    if (content != null) {
                        accumulatedContent.updateAndGet(current -> current + content);
                        callback.onTextReceived(content);
                    }

                    if ("stop".equals(result.getOutput().getChoices().get(0).getFinishReason())) {
                        callback.onComplete(accumulatedContent.toString());
                    }
                } else {
                    String errorMessage = String.format(
                        "DashScope流式调用返回不完整结果: 请求ID: %s, Usage: %s, Output: %s",
                        message != null ? message.getRequestId() : "N/A",
                        message != null && message.getUsage() != null ? JsonUtils.toJson(message.getUsage())
                            : "N/A",
                        message != null && message.getOutput() != null ? JsonUtils.toJson(message.getOutput())
                            : "N/A");
                    log.error(errorMessage);
                    callback.onError(new ApiException(new RuntimeException(errorMessage)));
                }
            });
        } catch (NoApiKeyException | ApiException | InputRequiredException e) {
            log.error("DashScope流式调用异常: {}", e.getMessage(), e);
            callback.onError(e);
        } catch (Exception e) {
            log.error("DashScope流式调用未知异常: {}", e.getMessage(), e);
            callback.onError(e);
        }
    }
}
