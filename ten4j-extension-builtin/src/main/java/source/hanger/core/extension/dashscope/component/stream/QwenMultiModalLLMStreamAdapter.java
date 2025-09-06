package source.hanger.core.extension.dashscope.component.stream;

import java.util.List;
import java.util.Optional;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput.Choice;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam.MultiModalConversationParamBuilder;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.protocol.Protocol;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolFunction;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.llm.BaseLLMStreamAdapter;
import source.hanger.core.extension.component.llm.ToolCallOutputFragment;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.extension.dashscope.common.DashScopeConstants;
import source.hanger.core.tenenv.TenEnv;

import static java.util.Optional.ofNullable;

/**
 * DashScope LLM 流服务实现类。
 * 负责与 DashScope LLM 进行交互，并实现 AbstractLLMStreamService 中定义的抽象方法。
 */
@Slf4j
public class QwenMultiModalLLMStreamAdapter
    extends BaseLLMStreamAdapter<MultiModalConversationResult, MultiModalMessage, ToolFunction> {

    private MultiModalConversation conversation;

    public QwenMultiModalLLMStreamAdapter(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        super(interruptionStateProvider, streamPipelineChannel);
    }

    @Override
    public void onStart(TenEnv env) {
        String baseUrl = env.getPropertyString("base_url").orElse(DashScopeConstants.BASE_URL);
        this.conversation = new MultiModalConversation(Protocol.HTTP.getValue(), baseUrl);
    }

    @Override
    protected Flowable<MultiModalConversationResult> getRawLlmFlowable(TenEnv env, List<MultiModalMessage> messages,
        List<ToolFunction> tools) {
        // 构建 GenerationParam
        MultiModalConversationParamBuilder paramBuilder
            = MultiModalConversationParam.builder()
            .apiKey(env.getPropertyString("api_key").orElseThrow(() -> new RuntimeException("api_key 为空")))
            .model(env.getPropertyString("model").orElseThrow(() -> new RuntimeException("model 为空")))
            .incrementalOutput(true)
            .messages(messages);

        if (CollectionUtils.isNotEmpty(tools)) {
            paramBuilder.tools(tools);
        }

        MultiModalConversationParam param = paramBuilder.build();

        try {
            // 使用注入的 DashScope Generation 客户端进行调用
            return conversation.streamCall(param)
                .subscribeOn(Schedulers.io());
        } catch (NoApiKeyException | UploadFileException e) {
            log.error("[{}] Error calling DashScope stream: {}", env.getExtensionName(), e.getMessage());
            return Flowable.error(e);
        }
    }

    @Override
    protected String extractTextFragment(MultiModalConversationResult result, StringBuilder fullTextBuffer, TenEnv env) {
        return ofNullable(result.getOutput())
            .map(output -> output.getChoices().stream().findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(Choice::getMessage)
            .map(MultiModalMessage::getContent)
            .map(contentList -> contentList.stream()
                .filter(c -> c.containsKey("text"))
                .findAny().map(c -> c.get("text"))
                .map(String::valueOf).orElse(""))
            .orElse(null);
    }

    @Override
    protected boolean isEndOfTextSegment(MultiModalConversationResult result) {
        // 根据 DashScope 的结束标志判断，例如 `finish_reason` 是 `stop` 或 `tool_calls`
        return ofNullable(result.getOutput())
            .map(output -> output.getChoices().stream().findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(Choice::getFinishReason)
            .map(reason -> "stop".equalsIgnoreCase(reason) || "tool_calls".equalsIgnoreCase(reason))
            .orElse(false);
    }

    @Override
    protected ToolCallOutputFragment extractAndConvertToolCallFragment(TenEnv env,
        MultiModalConversationResult result) {
        return ofNullable(result.getOutput())
            .map(output -> output.getChoices().stream().findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(Choice::getMessage)
            .map(MultiModalMessage::getToolCalls)
            .filter(toolCalls -> !toolCalls.isEmpty())
            .map(List::getFirst) // 假设目前只处理第一个工具调用
            .map(toolCallBase -> {
                if (toolCallBase instanceof ToolCallFunction toolCallFunction) {
                    return new ToolCallOutputFragment(toolCallFunction.getFunction().getName(),
                        toolCallFunction.getFunction().getArguments(),
                        result.getRequestId(),
                        toolCallBase.getId());
                }
                log.warn("[{}] 发现非 ToolCallFunction 类型的 ToolCallBase: {}",
                    env.getExtensionName(), toolCallBase.getClass().getName());
                return null;
            })
            .orElse(null);
    }

    @Override
    protected String getFinishReason(MultiModalConversationResult result) {
        return ofNullable(result.getOutput())
            .map(output -> output.getChoices().stream().findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(Choice::getFinishReason)
            .orElse(null);
    }

}
