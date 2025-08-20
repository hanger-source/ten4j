package source.hanger.core.extension.bailian.llm;

import java.util.List;
import java.util.Map;

import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.tools.ToolFunction;
import com.alibaba.dashscope.utils.JsonUtils;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.system.llm.BaseLLMExtension;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.SentenceProcessor;

/**
 * 简单的LLM扩展示例，用于演示如何处理不同类型的消息。
 */
@Slf4j
public class QwenLLMExtension extends BaseLLMExtension {

    private QwenLlmClient qwenLlmClient;
    private String prompt;

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        super.onConfigure(env, properties);
        log.info("[{}] QwenLLMExtension configuring", env.getExtensionName());

        String apiKey = (String)properties.get("api_key");
        String model = (String)properties.get("model");
        prompt = (String)properties.get("prompt");

        log.info("[{}] Config: model={}, api_key={}, prompt={}", env.getExtensionName(), // [{}] 移除max_history日志
            model, apiKey != null && !apiKey.isEmpty() ? "**********" : "NOT_SET", prompt);

        if (apiKey == null || model == null || apiKey.isEmpty() || model.isEmpty()) {
            log.error("[{}] API Key or Model is not set. Please configure in manifest.json/property.json.", env.getExtensionName());
            return;
        }

        qwenLlmClient = new QwenLlmClient(apiKey, model);
    }

    @Override
    public void onInit(TenEnv env) {
        super.onInit(env);
        log.info("[{}] Extension initialized", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[{}] Extension starting", env.getExtensionName());
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
        log.info("[{}] Extension stopping", env.getExtensionName());
    }

    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
        log.info("[{}] Extension de-initialized", env.getExtensionName());
    }

    @Override
    protected Flowable<GenerationResult> onRequestLLM(TenEnv env, List<Message> messages, List<ToolFunction> tools) {

        return qwenLlmClient.streamChatCompletion(messages, tools)
            .doOnComplete(() -> {
                log.info("[{}] LLM流处理完成。", env.getExtensionName());
            })
            .doOnError(throwable -> {
                log.error("[{}] Stream chat completion failed", throwable.getMessage(), throwable);
            });
    }

    // [{}] 实现BaseLLMExtension的抽象方法，处理纯文本流式生成结果
    @Override
    protected void processLlmStreamTextOutput(GenerationResult result,
        source.hanger.core.message.Message originalMessage, TenEnv env) {

        if (result != null && result.getOutput() != null && result.getOutput().getChoices() != null
            && !result.getOutput().getChoices().isEmpty()) {
            String content = result.getOutput().getChoices().get(0).getMessage().getContent();
            boolean isStop = "stop".equals(result.getOutput().getChoices().get(0).getFinishReason());

            // 直接发送文本，并累积完整内容
            if (content != null && !content.isEmpty()) {
                currentLlmResponse.append(content);

                // 使用句子解析逻辑，调用 SentenceProcessor
                SentenceProcessor.SentenceParsingResult parsingResult = SentenceProcessor.parseSentences(sentenceFragment, content);
                for (String sentence : parsingResult.getSentences()) {
                    sendTextOutput(env, originalMessage, sentence, false);
                    log.info("[{}] LLM文本输出: sentence={}", env.getExtensionName(), sentence);
                }
                sentenceFragment = parsingResult.getRemainingFragment();
            }
            if (isStop) {
                // 确保发送剩余的片段，并标记为结束
                if (!sentenceFragment.isEmpty()) {
                    sendTextOutput(env, originalMessage, sentenceFragment, true);
                    log.info("[{}] LLM文本输出: finalFragment={}", env.getExtensionName(),
                        sentenceFragment);
                    sentenceFragment = ""; // 清空
                } else if (currentLlmResponse.isEmpty()) {
                    // 如果没有任何内容，也发送一个结束标志
                    sendTextOutput(env, originalMessage, "", true);
                }

                // LLM 回复结束，将完整内容添加到历史记录
                if (!currentLlmResponse.isEmpty()) {
                    llmHistoryManager.onMsg("assistant", currentLlmResponse.toString()); // [{}] 调用 LLMHistoryManager
                    currentLlmResponse.setLength(0); // 清空，为下一次回复做准备
                }
            }

        } else {
            String errorMessage = String.format(
                "[{}] DashScope流式调用返回不完整结果: 请求ID: %s, Usage: %s, Output: %s",
                result != null ? result.getRequestId() : "N/A",
                result != null && result.getUsage() != null ? JsonUtils.toJson(result.getUsage())
                    : "N/A",
                result != null && result.getOutput() != null ? JsonUtils.toJson(result.getOutput())
                    : "N/A");
            log.error(errorMessage);
        }
    }

    @Override
    protected void onCallChatCompletion(TenEnv env, Command originalCommand, List<Message> messages, List<ToolFunction> tools) {
        log.info("[{}] Received command for chat completion with messages and tools.", env.getExtensionName());
        // 用户消息已经由BaseLLMExtension的handleChatCompletionCall添加到历史
        // 这里不需要额外处理 messages 或 tools，因为onRequestLLM 会使用 llmHistoryManager 的内容
        // 而是直接触发 LLM 调用，通过 streamProcessor.onNext 传递
        streamProcessor.onNext(new StreamPayload<>(onRequestLLM(env, messages, tools), originalCommand));
    }

    @Override
    protected void onCancelLLM(TenEnv env) {
        log.info("[{}] Cancelling current LLM request (handled by Flowable disposal)",
            env.getExtensionName());
    }

    @Override
    public void flushInputItems(TenEnv env, Command command) {
        super.flushInputItems(env, command);
        log.debug("[{}] Sent CMD_OUT_FLUSH in response to CMD_IN_FLUSH.", env.getExtensionName());
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        // [{}] 所有工具相关命令和通用命令都在BaseLLMExtension中处理，这里直接调用父类方法
        super.onCmd(env, command);
        String cmdName = command.getName();
        log.debug("[{}] QwenLLMExtension 收到命令：{}", cmdName, env.getExtensionName());
    }

    // [{}] 实现BaseLLMExtension的抽象方法，提供QwenLLMExtension的系统提示词
    @Override
    protected String getSystemPrompt() {
        return this.prompt;
    }
}