package source.hanger.core.extension.bailian.llm;

import java.util.List;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationParam.GenerationParamBuilder;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.tools.ToolFunction;

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

    public QwenLlmClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.generation = new Generation();
    }

    public Flowable<GenerationResult> streamChatCompletion(List<Message> messages, List<ToolFunction> tools) {
        GenerationParamBuilder paramBuilder = GenerationParam.builder()
            .apiKey(apiKey)
            .model(model)
            .messages(messages)
            .resultFormat(GenerationParam.ResultFormat.MESSAGE)
            .incrementalOutput(true);

        if (tools != null && !tools.isEmpty()) {
            paramBuilder.tools(tools);
        }

        GenerationParam param = paramBuilder.build();

        Flowable<GenerationResult> resultFlowable;
        try {
            resultFlowable = generation.streamCall(param);
        } catch (NoApiKeyException | InputRequiredException e) {
            return Flowable.error(e);
        }

        return resultFlowable;
    }
}
