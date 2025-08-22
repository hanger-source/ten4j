package source.hanger.core.extension.component.llm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.SentenceProcessor;

/**
 * LLM 流服务抽象基类。
 * 负责 LLM 原始输出的复杂解析、文本聚合、通用工具调用片段的生成，并将其转换为更高级的“逻辑块”推送到主管道。
 *
 * @param <GENERATION_RAW_RESULT> LLM 原始响应的类型（例如 DashScope 的 GenerationResult）。
 * @param <MESSAGE>           LLM 对话消息的类型（例如 DashScope 的 Message）。
 * @param <TOOL_FUNCTION>     LLM 工具函数定义的类型（例如 DashScope 的 ToolCallFunction）。
 */
@Slf4j
public abstract class BaseLLMStreamAdapter<GENERATION_RAW_RESULT, MESSAGE, TOOL_FUNCTION>
    implements LLMStreamAdapter<MESSAGE, TOOL_FUNCTION> {

    protected final InterruptionStateProvider interruptionStateProvider;
    protected final StreamPipelineChannel<OutputBlock> streamPipelineChannel;
    protected final Map<String, ToolCallOutputFragment> accumulatingToolCallFragments = new ConcurrentHashMap<>();

    protected final String TEXT_BUFFER_STATE = "textBuffer";
    protected final String FULL_TEXT_BUFFER_STATE = "fullTextBuffer";

    /**
     * 构造函数。
     *
     * @param interruptionStateProvider 中断状态提供者。
     * @param streamPipelineChannel     流管道管理器。
     */
    public BaseLLMStreamAdapter(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        this.interruptionStateProvider = interruptionStateProvider;
        this.streamPipelineChannel = streamPipelineChannel;
    }

    @Override
    public void onRequestLLMAndProcessStream(TenEnv env, List<MESSAGE> messages, List<TOOL_FUNCTION> tools,
        Message originalMessage) {
        log.info("[{}] 开始请求LLM并处理流. 原始消息ID: {}", env.getExtensionName(),
            originalMessage.getId());

        Map<String, Object> streamContexts = initStreamContexts(env, messages, tools);

        // 获取 LLM 原始响应流
        Flowable<GENERATION_RAW_RESULT> rawLlmFlowable = getRawLlmFlowable(env, messages, tools);

        // 转换原始 LLM 流为 LLMOutputBlock 流，实现真正的流式处理
        Flowable<PipelinePacket<OutputBlock>> transformedOutputFlowable = rawLlmFlowable
            .flatMap(
                result -> transformSingleGenerationResult(result, originalMessage, streamContexts, env))
            // 中断检测，flush后中断当前流处理
            .takeWhile(_ -> !interruptionStateProvider.isInterrupted())
            .doOnError(error -> {
                log.error("[{}] LLM流转换错误. 原始消息ID: {}. 错误: {}", env.getExtensionName(),
                    originalMessage.getId(), error.getMessage(), error);
                // 可以在这里将错误信息包装成 LLMOutputBlock 推送到管道，或进行其他错误处理
            })
            .doOnComplete(() -> {
                // 确保所有日志都带有前缀
                log.info("[{}] LLM原始流处理完成. 原始消息ID: {}", env.getExtensionName(),
                    originalMessage.getId());
            });

        streamPipelineChannel.submitStreamPayload(transformedOutputFlowable, env); // 直接提交 transformedOutputFlowable
    }

    protected Map<String, Object> initStreamContexts(TenEnv env, List<MESSAGE> messages, List<TOOL_FUNCTION> tools) {
        Map<String, Object> streamContexts = new HashMap<>();
        streamContexts.put(TEXT_BUFFER_STATE, new StringBuilder());
        streamContexts.put(FULL_TEXT_BUFFER_STATE, new StringBuilder());
        return streamContexts;
    }

    /**
     * 辅助方法：处理单个 LLM 原始响应结果。
     * 从结果中提取文本片段和工具调用片段，并将其转换为 Flowable<PipelinePacket<LLMOutputBlock>>。
     *
     * @param result          LLM 原始响应结果。
     * @param originalMessage 原始消息。
     * @param env             当前的 TenEnv 环境。
     * @return 包含 PipelinePacket 的 Flowable 流。
     */
    protected Flowable<PipelinePacket<OutputBlock>> transformSingleGenerationResult(
        GENERATION_RAW_RESULT result,
        Message originalMessage,
        Map<String, Object> streamContexts, TenEnv env
    ) {
        StringBuilder textBuffer = (StringBuilder)streamContexts.get(TEXT_BUFFER_STATE);
        StringBuilder fullTextBuffer = (StringBuilder)streamContexts.get(FULL_TEXT_BUFFER_STATE);

        List<PipelinePacket<OutputBlock>> packetsToEmit = new java.util.ArrayList<>();

        // 提前获取 finishReason
        String finishReason = getFinishReason(result);
        boolean isStreamEnding = "stop".equalsIgnoreCase(finishReason) ||
            "tool_calls".equalsIgnoreCase(finishReason) ||
            "max_tokens".equalsIgnoreCase(finishReason);
        boolean endOfSegment = isEndOfTextSegment(result); // 用于判断是否是当前LLM响应的末尾片段

        // 1. 处理文本片段并聚合为句子或逻辑块
        processTextStreamResult(result, originalMessage, textBuffer, fullTextBuffer, env, isStreamEnding, endOfSegment,
            packetsToEmit);

        // 2. 处理工具调用片段并聚合
        processToolCallStreamResult(result, originalMessage, env, finishReason, packetsToEmit);

        // 3. 处理其他片段
        processOtherStreamResult(result, originalMessage, env, finishReason, packetsToEmit, streamContexts);

        return Flowable.fromIterable(packetsToEmit);
    }

    protected void processOtherStreamResult(GENERATION_RAW_RESULT result, Message originalMessage, TenEnv env,
        String finishReason, List<PipelinePacket<OutputBlock>> packetsToEmit, Map<String, Object> requestStates) {

    }

    /**
     * 辅助方法：处理 LLM 文本流的片段。
     * 从结果中提取文本片段，并将其聚合为句子或逻辑块，然后添加到 packetsToEmit 列表中。
     *
     * @param result          LLM 原始响应结果。
     * @param originalMessage 原始消息。
     * @param textBuffer      textBuffer
     * @param fullTextBuffer  fullTextBuffer
     * @param env             当前的 TenEnv 环境。
     * @param isStreamEnding  是否流已结束。
     * @param endOfSegment    是否是文本段的末尾。
     * @param packetsToEmit   收集 PipelinePacket 的列表。
     */
    protected void processTextStreamResult(
        GENERATION_RAW_RESULT result,
        Message originalMessage,
        StringBuilder textBuffer, StringBuilder fullTextBuffer, TenEnv env,
        boolean isStreamEnding,
        boolean endOfSegment,
        List<PipelinePacket<OutputBlock>> packetsToEmit) {
        String textFragment = extractTextFragment(result);
        if (textFragment != null && !textFragment.isEmpty()) {
            fullTextBuffer.append(textFragment);
            // 使用 SentenceProcessor 解析句子，并更新 textBuffer 为剩余片段
            SentenceProcessor.SentenceParsingResult parsingResult =
                SentenceProcessor.parseSentences(textBuffer.toString(), textFragment);

            textBuffer.setLength(0); // 清空缓冲区
            textBuffer.append(parsingResult.getRemainingFragment()); // 更新为剩余片段

            // 发出所有完整的句子
            for (String sentence : parsingResult.getSentences()) {
                log.debug("[{}] 发出LLMTextOutputBlock. 文本: {}", env.getExtensionName(), sentence);
                packetsToEmit.add(new PipelinePacket<>(
                    new TextOutputBlock(originalMessage.getId(), sentence, false), // 是片段
                    originalMessage
                ));
            }
        }

        // 如果是流结束或当前文本段的结束，并且缓冲区中还有文本，则发出剩余文本作为最终片段
        if ((isStreamEnding || endOfSegment)) {
            if (!fullTextBuffer.isEmpty()) {
                packetsToEmit.add(new PipelinePacket<>(
                    new TextOutputBlock(originalMessage.getId(), textBuffer.toString(), true,
                        fullTextBuffer.toString()),
                    originalMessage
                ));
            }
            textBuffer.setLength(0);
            fullTextBuffer.setLength(0);
        }
    }

    /**
     * 辅助方法：处理 LLM 工具调用流的片段。
     * 从结果中提取工具调用片段，并将其聚合，然后添加到 packetsToEmit 列表中。
     *
     * @param result          LLM 原始响应结果。
     * @param originalMessage 原始消息。
     * @param env             当前的 TenEnv 环境。
     * @param finishReason    LLM 的结束原因。
     * @param packetsToEmit   收集 PipelinePacket 的列表。
     */
    protected void processToolCallStreamResult(
        GENERATION_RAW_RESULT result,
        Message originalMessage,
        TenEnv env,
        String finishReason,
        List<PipelinePacket<OutputBlock>> packetsToEmit) {
        ToolCallOutputFragment rawToolCallOutputFragment = extractAndConvertToolCallFragment(env, result);
        // 聚合工具调用片段
        ToolCallOutputBlock completeToolCall = processToolCallFragmentAggregation(rawToolCallOutputFragment,
            finishReason,
            env, originalMessage);

        if (completeToolCall != null) {
            log.info("[{}] 发现并聚合完成工具调用. 工具ID: {}, 名称: {}. 将提交给ToolRegistryAndCaller.",
                env.getExtensionName(), completeToolCall.getId(), completeToolCall.getToolName());
            packetsToEmit.add(new PipelinePacket<>(completeToolCall, originalMessage));
        }
    }

    @Override
    public void onCancelLLM(TenEnv env) {
        log.info("[{}] 收到取消LLM请求", env.getExtensionName());
    }

    /**
     * 抽象方法：获取 LLM 供应商的原始响应流。
     * 由具体实现类（例如 DashScopeLLMStreamService）提供。
     *
     * @param env      当前的 TenEnv 环境。
     * @param messages 发送给 LLM 的消息列表。
     * @param tools    提供给 LLM 的工具列表。
     * @return 包含原始 LLM 响应的 Flowable 流。
     */
    protected abstract Flowable<GENERATION_RAW_RESULT> getRawLlmFlowable(TenEnv env, List<MESSAGE> messages,
        List<TOOL_FUNCTION> tools);

    /**
     * 抽象方法：从原始 LLM 响应中提取文本片段。
     * 由具体实现类提供。
     *
     * @param result 原始 LLM 响应。
     * @return 提取的文本片段，如果无文本则返回 null 或空字符串。
     */
    protected abstract String extractTextFragment(GENERATION_RAW_RESULT result);

    /**
     * 抽象方法：判断当前原始 LLM 响应是否表示一个文本段的结束。
     * 由具体实现类提供。
     *
     * @param result 原始 LLM 响应。
     * @return 如果是文本段的最终片段则返回 true。
     */
    protected abstract boolean isEndOfTextSegment(GENERATION_RAW_RESULT result);

    /**
     * 抽象方法：从原始 LLM 响应中提取并转换为通用工具调用片段。
     * 这是实现 LLMStreamService 与 ToolRegistryAndCaller 解耦的关键。
     * 由具体实现类提供。
     *
     * @param env    当前的 TenEnv 环境。
     * @param result 原始 LLM 响应。
     * @return 转换后的 CommonToolCallFragment，如果无工具调用则返回 null。
     */
    protected abstract ToolCallOutputFragment extractAndConvertToolCallFragment(TenEnv env,
        GENERATION_RAW_RESULT result);

    /**
     * 抽象方法：从原始 LLM 响应中获取 LLM 的结束原因（例如 stop, tool_calls）。
     * 由具体实现类提供。
     *
     * @param result 原始 LLM 响应。
     * @return LLM 结束原因的字符串表示。
     */
    protected abstract String getFinishReason(GENERATION_RAW_RESULT result);

    /**
     * 辅助方法：处理工具调用片段的聚合逻辑。
     * 将传入的片段累积到 accumulatingToolCallFragments 中，如果形成完整工具调用则返回，否则返回 null。
     *
     * @param incomingFragment 当前收到的工具调用片段。
     * @param finishReason     LLM 的结束原因。
     * @return 完整的 LLMToolCallOutputBlock (如果已聚合完成)，否则返回 null。
     */
    protected ToolCallOutputBlock processToolCallFragmentAggregation(ToolCallOutputFragment incomingFragment,
        String finishReason, TenEnv env, Message originalMessage) {
        if (incomingFragment == null) {
            return null; // 没有传入片段，直接返回 null
        }

        String id = incomingFragment.id();
        if (id == null) {
            log.warn("[{}] 收到没有toolCallId的工具调用片段，将忽略. 名称: {}", env.getExtensionName(),
                incomingFragment.name());
            return null; // 没有 ID 无法累积
        }

        accumulatingToolCallFragments.compute(id, (_, existingFragment) -> {
            if (existingFragment == null) {
                return incomingFragment;
            } else {
                String newArguments = incomingFragment.argumentsJson() != null ? incomingFragment.argumentsJson()
                    : "";
                return new ToolCallOutputFragment(
                    existingFragment.name(),
                    existingFragment.argumentsJson() + newArguments,
                    id,
                    existingFragment.toolCallId()
                );
            }
        });

        // 如果结束原因是工具调用完成，则返回当前累积的完整工具调用
        if ("tool_calls".equalsIgnoreCase(finishReason)) {
            ToolCallOutputFragment completeToolCall = accumulatingToolCallFragments.remove(id);
            if (completeToolCall != null) {
                log.info("[{}] 工具调用 {} 聚合完成并移除. ID: {}", env.getExtensionName(), completeToolCall.name(),
                    completeToolCall.id());
                return new ToolCallOutputBlock(
                    originalMessage.getId(),
                    completeToolCall.name(),
                    completeToolCall.argumentsJson(),
                    null,
                    completeToolCall.id(),
                    completeToolCall.toolCallId()
                );
            }
            return null; // 即使 finishReason 是 tool_calls，如果没有完整工具调用，也返回 null
        }

        return null; // 工具调用尚未完成
    }
}
