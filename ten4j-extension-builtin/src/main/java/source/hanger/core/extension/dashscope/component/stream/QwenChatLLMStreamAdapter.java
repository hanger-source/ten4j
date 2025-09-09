package source.hanger.core.extension.dashscope.component.stream;

import java.util.List;
import java.util.Optional;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationOutput.Choice;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.TranslationOptions;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.protocol.Protocol;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolFunction;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.llm.BaseLLMStreamAdapter;
import source.hanger.core.extension.component.llm.ToolCallOutputFragment;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.extension.dashscope.common.DashScopeConstants;
import source.hanger.core.tenenv.TenEnv;

/**
 * DashScope LLM 流服务实现类。
 * 负责与 DashScope LLM 进行交互，并实现 AbstractLLMStreamService 中定义的抽象方法。
 */
@Slf4j
public class QwenChatLLMStreamAdapter extends BaseLLMStreamAdapter<GenerationResult, Message, ToolFunction> {

    private Generation generation;

    public QwenChatLLMStreamAdapter(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        super(interruptionStateProvider, streamPipelineChannel);
    }

    @Override
    public void onStart(TenEnv env) {
        String baseUrl = env.getPropertyString("base_url").orElse(DashScopeConstants.BASE_URL);
        this.generation = new Generation(Protocol.HTTP.getValue(), baseUrl);
    }

    @Override
    protected Flowable<GenerationResult> getRawLlmFlowable(TenEnv env, List<Message> messages,
        List<ToolFunction> tools) {
        // 构建 GenerationParam
        String model = env.getPropertyString("model").orElseThrow(() -> new RuntimeException("model 为空"));
        boolean isTranslateModel = StringUtils.containsIgnoreCase(model, "mt");

        GenerationParam.GenerationParamBuilder paramBuilder = GenerationParam.builder()
            .apiKey(env.getPropertyString("api_key").orElseThrow(() -> new RuntimeException("api_key 为空")))
            .model(model)
            .messages(messages)
            .enableSearch(env.getProperty("enable_search").map(String::valueOf).map(Boolean::parseBoolean).orElse(false))
            .resultFormat(GenerationParam.ResultFormat.MESSAGE)
            .incrementalOutput(true);

        if (isTranslateModel) {
            paramBuilder.translationOptions(TranslationOptions.builder()
                .sourceLang("auto")
                .targetLang("English")
                .build());
        }

        if (CollectionUtils.isNotEmpty(tools)) {
            paramBuilder.tools(tools);
        }

        StopWatch stopWatch = StopWatch.createStarted();
        GenerationParam param = paramBuilder.build();

        try {
            // 使用注入的 DashScope Generation 客户端进行调用
            return generation.streamCall(param)
                .subscribeOn(Schedulers.io())
                .doOnNext(result -> {
                    if (!stopWatch.isStopped()) {
                        stopWatch.stop();
                        log.info("[{}] DashScope Generation channelId={} 首字输出 elapsed_time={}ms ",
                            env.getExtensionName(), streamPipelineChannel.uuid(), stopWatch.getTime());
                    }
                }).doOnTerminate(() -> {
                    if (!stopWatch.isStopped()) {
                        stopWatch.stop();
                    }
                }).doOnComplete(() -> {
                    log.info("[{}] DashScope Generation channelId={} 流输出完成", env.getExtensionName(),
                        streamPipelineChannel.uuid());
                }).doOnError(e -> {
                    log.error("[{}] DashScope Generation channelId={} 执行过程异常: {}", env.getExtensionName(),
                        streamPipelineChannel.uuid(), e.getMessage());
                }).doOnCancel(() -> {
                    log.info("[{}] DashScope Generation channelId={} 流输出取消", env.getExtensionName(),
                        streamPipelineChannel.uuid());
                }).doOnTerminate(() -> {
                    if (!stopWatch.isStopped()) {
                        stopWatch.stop();
                    }
                });
        } catch (NoApiKeyException | InputRequiredException e) {
            log.error("[{}] Error calling DashScope stream: {}", env.getExtensionName(), e.getMessage());
            return Flowable.error(e);
        }
    }

    @Override
    protected String extractTextFragment(GenerationResult result, StringBuilder fullTextBuffer, TenEnv env) {
        String text = Optional.ofNullable(result.getOutput())
            .map(output -> output.getChoices().stream().findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(Choice::getMessage)
            .map(Message::getContent)
            .orElse(null);

        String model = env.getPropertyString("model").orElseThrow(() -> new RuntimeException("model 为空"));
        boolean isTranslateModel = StringUtils.containsIgnoreCase(model, "mt");
        if (isTranslateModel) {
            // Qwen-MT模型暂时不支持增量式流式输出。不支持增量，兼容为增量输出
            return StringUtils.substringAfter(text, fullTextBuffer.toString());
        }
        return text;
    }

    @Override
    protected boolean isEndOfTextSegment(GenerationResult result) {
        // 根据 DashScope 的结束标志判断，例如 `finish_reason` 是 `stop` 或 `tool_calls`
        return Optional.ofNullable(result.getOutput())
            .map(output -> output.getChoices().stream().findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(Choice::getFinishReason)
            .map(reason -> "stop".equalsIgnoreCase(reason) || "tool_calls".equalsIgnoreCase(reason))
            .orElse(false);
    }

    @Override
    protected ToolCallOutputFragment extractAndConvertToolCallFragment(TenEnv env, GenerationResult result) {
        return Optional.ofNullable(result.getOutput())
            .map(output -> output.getChoices().stream().findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(Choice::getMessage)
            .map(Message::getToolCalls)
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
    protected String getFinishReason(GenerationResult result) {
        return Optional.ofNullable(result.getOutput())
            .map(output -> output.getChoices().stream().findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(Choice::getFinishReason)
            .orElse(null);
    }

}
