package source.hanger.core.extension.base;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.tool.LLMToolMetadata;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.extension.component.flush.DefaultFlushOperationCoordinator;
import source.hanger.core.extension.component.flush.FlushOperationCoordinator;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.llm.LLMStreamAdapter;
import source.hanger.core.extension.component.llm.TextOutputBlock;
import source.hanger.core.extension.component.llm.ToolCallOutputBlock;
import source.hanger.core.extension.component.stream.DefaultStreamPipelineChannel;
import source.hanger.core.extension.component.stream.StreamOutputBlockConsumer;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.extension.component.tool.LLMToolOrchestrator;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;

import static source.hanger.core.common.ExtensionConstants.CMD_IN_FLUSH;
import static source.hanger.core.common.ExtensionConstants.CMD_IN_ON_USER_JOINED;
import static source.hanger.core.common.ExtensionConstants.CMD_OUT_FLUSH;
import static source.hanger.core.common.ExtensionConstants.CMD_PROPERTY_TOOL;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_REGISTER;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_IS_FINAL;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;
import static source.hanger.core.extension.component.output.MessageOutputSender.sendTextOutput;

/**
 * LLM 扩展的抽象基类。
 * 此类提供了 LLM Agent 核心组件的通用结构和生命周期管理，并抽象了与具体 LLM 供应商交互的细节。
 * 子类需要提供具体的 LLMContextManager, LLMStreamAdapter, ToolRegistryAndCaller 实现，
 * 并处理 LLM 特定消息的解析。
 *
 * @param <MESSAGE>       LLM 对话消息的类型。
 * @param <TOOL_FUNCTION> LLM 工具函数定义的类型。
 */
@Slf4j
public abstract class BaseLLMExtension<MESSAGE, TOOL_FUNCTION> extends BaseExtension {

    // 从 BaseLLMToolExtension 迁移的成员变量
    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected FlushOperationCoordinator flushOperationCoordinator;
    protected StreamPipelineChannel<OutputBlock> streamPipelineChannel;
    protected LLMContextManager<MESSAGE> llmContextManager;
    protected LLMStreamAdapter<MESSAGE, TOOL_FUNCTION> llmStreamAdapter;
    protected LLMToolOrchestrator<TOOL_FUNCTION> LLMToolOrchestrator;

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        log.info("[{}] 配置中，初始化核心组件。", env.getExtensionName());

        // 2. 初始化 LLMContextManager (由子类提供具体实现)
        this.llmContextManager = createLLMContextManager(env, () -> (String)properties.get("system_prompt"));
        log.info("[{}] 配置中，初始化 LLMContextManager。", env.getExtensionName());

        // 3. 初始化 StreamPipelineManager，需要 StreamItemHandler (由子类提供具体实现)
        this.streamPipelineChannel = createStreamPipelineChannel(createStreamLLMOutputBlockConsumer());
        log.info("[{}] 配置中，初始化 StreamPipelineManager。", env.getExtensionName());

        // 4. 初始化 LLMStreamAdapter (由子类提供具体实现)
        this.llmStreamAdapter = createLLMStreamAdapter();
        log.info("[{}] 配置中，初始化 LLMStreamAdapter。", env.getExtensionName());

        // 5. 初始化 ToolRegistryAndCaller (由子类提供具体实现)
        this.LLMToolOrchestrator = createToolOrchestrator();
        log.info("[{}] 配置中，初始化 ToolRegistryAndCaller。", env.getExtensionName());

        // 6. 初始化 FlushOperationCoordinator (由子类提供具体实现，或使用通用实现)
        this.flushOperationCoordinator = createFlushOperationCoordinator(extensionStateProvider,
            streamPipelineChannel, (currentEnv) -> {
                // LLMStreamAdapter 的 onCancelLLM 方法被调用
                llmStreamAdapter.onCancelLLM(currentEnv);
            });
        log.info("[{}] 配置中，初始化 FlushOperationCoordinator。", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[{}] BaseLLMExtension 启动，初始化管道。", env.getExtensionName());
        streamPipelineChannel.initPipeline(env);
    }

    @Override
    public void onStop(TenEnv env) {
        log.info("[{}] BaseLLMExtension 停止，销毁管道。", env.getExtensionName());
        super.onStop(env);
        streamPipelineChannel.disposeCurrent();
    }

    @Override
    public void onDestroy(TenEnv env) {
        log.info("[{}] BaseLLMExtension 销毁，清理 LLM 上下文管理器。", env.getExtensionName());
        if (llmContextManager != null) {
            llmContextManager.onDestroy();
        }
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage dataMessage) {
        if (!isRunning()) {
            log.warn("[{}] Extension未运行，忽略 DataMessage。", env.getExtensionName());
            return;
        }

        String userText = dataMessage.getPropertyString(DATA_OUT_PROPERTY_TEXT).orElse("");
        if (!dataMessage.getPropertyBool(DATA_OUT_PROPERTY_IS_FINAL).orElse(false)) {
            log.info("[{}] LLM扩展收到非最终数据: text={}", env.getExtensionName(), userText);
            return;
        }

        // 将用户输入添加到上下文
        if (!userText.isEmpty()) {
            llmContextManager.onUserMsg(userText);

            List<MESSAGE> messagesForLlm = llmContextManager.getMessagesForLLM();
            List<TOOL_FUNCTION> registeredTools = LLMToolOrchestrator.getRegisteredToolFunctions();
            // 请求 LLM 并处理流
            llmStreamAdapter.onRequestLLMAndProcessStream(env, messagesForLlm, registeredTools, dataMessage);
        }
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        if (!isRunning()) {
            log.warn("[{}] Extension未运行，忽略 Command。", env.getExtensionName());
            return;
        }

        if (CMD_IN_ON_USER_JOINED.equals(command.getName())) {
            onUserJoined(env, command);
            CommandResult userJoinedResult = CommandResult.success(command, "User joined.");
            env.sendResult(userJoinedResult);
            return;
        }

        if (CMD_TOOL_REGISTER.equals(command.getName())) {
            try {
                // 工具元数据
                String toolJson = (String)command.getProperty(CMD_PROPERTY_TOOL);
                LLMToolMetadata LLMToolMetadata = objectMapper.readValue(toolJson, LLMToolMetadata.class);
                LLMToolOrchestrator.registerTool(LLMToolMetadata);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        // 处理 CMD_FLUSH 命令
        if (CMD_IN_FLUSH.equals(command.getName())) {
            log.info("[{}] 收到 CMD_FLUSH 命令，执行刷新操作并重置历史。", env.getExtensionName());
            flushOperationCoordinator.triggerFlush(env);
            env.sendCmd(GenericCommand.create(CMD_OUT_FLUSH, command.getId(), command.getType()));
            return;
        }
    }

    /**
     * 抽象方法：创建 LLMContextManager 实例。
     * 子类应返回 LLMContextManager 的具体实现，例如 DashScopeLLMContextManager。
     */
    protected abstract LLMContextManager<MESSAGE> createLLMContextManager(TenEnv env, Supplier<String> systemPromptSupplier);

    protected void onUserJoined(TenEnv env, Command command) {
        log.info("[{}] LLM扩展收到用户加入事件", env.getExtensionName());
        // Python实现中这里会发送 greeting 消息，Java版本目前只返回OK
        String greeting = env.getPropertyString("greeting").orElse("你好啊");
        if (!greeting.isEmpty()) {
            try {
                llmContextManager.onAssistantMsg(greeting); // 调用 LLMHistoryManager
                sendTextOutput(env, command, greeting, true);
                log.info("[{}] Greeting {} sent to user.", env.getExtensionName(), greeting);
            } catch (Exception e) {
                log.error("[{}] Failed to send greeting [{}], error: {}", env.getExtensionName(), greeting,
                    e.getMessage(), e);
            }
            flushOperationCoordinator.triggerFlush(env);
        }
    }

    protected StreamOutputBlockConsumer<OutputBlock> createStreamLLMOutputBlockConsumer() {
        return (item, originalMessage, env) -> {
            if (item instanceof TextOutputBlock textBlock) {
                // 文本块
                log.info("[{}] Stream输出 (Text): {}", env.getExtensionName(), textBlock.getText());
                // 发送 TextOutput
                sendTextOutput(env, originalMessage, textBlock.getText(),
                    textBlock.isFinalSegment());

                if (textBlock.isFinalSegment()) {
                    llmContextManager.onAssistantMsg(textBlock.getFullText());
                    log.info("[{}] Stream输出 (Text) 最终片段，已添加到历史。", env.getExtensionName());
                }

            } else if (item instanceof ToolCallOutputBlock toolCallBlock) {
                // 工具调用块
                log.info("[{}] Stream输出 (Tool Call): Name={}, Arguments={}, Tool ID={}",
                    env.getExtensionName(), toolCallBlock.getToolName(), toolCallBlock.getArgumentsJson(),
                    toolCallBlock.getId());
                LLMToolOrchestrator.processToolCall(env, toolCallBlock, originalMessage);
            }
        };
    }

    /**
     * 抽象方法：创建 StreamPipelineManager 实例。
     * 子类应返回 StreamPipelineManager 的具体实现，例如 DefaultStreamPipelineManager。
     *
     * @param streamOutputBlockConsumer 流项目处理器。
     */
    protected StreamPipelineChannel<OutputBlock> createStreamPipelineChannel(
        StreamOutputBlockConsumer<OutputBlock> streamOutputBlockConsumer) {
        return new DefaultStreamPipelineChannel(extensionStateProvider, streamOutputBlockConsumer);
    }

    /**
     * 抽象方法：创建 LLMStreamAdapter 实例。
     * 子类应返回 LLMStreamAdapter 的具体实现，例如 DashScopeLLMStreamAdapter。
     */
    protected abstract LLMStreamAdapter<MESSAGE, TOOL_FUNCTION> createLLMStreamAdapter();

    /**
     * 抽象方法：创建 ToolRegistryAndCaller 实例。
     * 子类应返回 ToolRegistryAndCaller 的具体实现，例如 DashScopeToolRegistryAndCaller。
     */
    protected abstract LLMToolOrchestrator<TOOL_FUNCTION> createToolOrchestrator();

    /**
     * 抽象方法：创建 FlushOperationCoordinator 实例。
     * 子类应返回 FlushOperationCoordinator 的具体实现，例如 DefaultFlushOperationCoordinator。
     *
     * @param interruptionStateProvider 中断状态提供者。
     * @param streamPipelineChannel     流管道管理器。
     * @param onCancelFlushCallback     用于通知 LLMStreamAdapter 执行取消操作的回调函数。
     */
    protected FlushOperationCoordinator createFlushOperationCoordinator(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel,
        java.util.function.Consumer<TenEnv> onCancelFlushCallback) {
        return new DefaultFlushOperationCoordinator(interruptionStateProvider,
            streamPipelineChannel, onCancelFlushCallback);
    }
}