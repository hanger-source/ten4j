package source.hanger.core.extension.component.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.tool.LLMToolMetadata;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.extension.component.llm.LLMStreamAdapter;
import source.hanger.core.extension.component.llm.ToolCallOutputBlock;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL_PROPERTY_ARGUMENTS;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL_PROPERTY_NAME;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL_PROPERTY_TOOL_CALL_ID;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_PROPERTY_ASSISTANT_MESSAGE;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_PROPERTY_SECOND_ROUND;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_PROPERTY_TOOL_CALL_CONTENT;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;

/**
 * ToolRegistryAndCallerImpl 是工具注册和调用的实现类。
 * 它负责管理 LLM 可用的工具，并在 LLM 请求工具调用时执行它们。
 * 同时，它也负责协调 LLM Agent 循环的继续，并在工具执行完成后将结果反馈给 LLM。
 */
@Slf4j
public abstract class BaseLLMToolOrchestrator<MESSAGE, LLM_TOOL_FUNCTION> implements
    LLMToolOrchestrator<LLM_TOOL_FUNCTION> {

    protected final LLMContextManager<MESSAGE> llmContextManager;
    private final Map<String, LLMToolMetadata> toolMap = new HashMap<>();
    private final LLMStreamAdapter<MESSAGE, LLM_TOOL_FUNCTION> llmStreamAdapter;

    private final List<Disposable> disposables;

    public BaseLLMToolOrchestrator(
        LLMContextManager<MESSAGE> llmContextManager,
        LLMStreamAdapter<MESSAGE, LLM_TOOL_FUNCTION> llmStreamAdapter) {
        this.llmContextManager = llmContextManager;
        this.llmStreamAdapter = llmStreamAdapter;
        disposables = new ArrayList<>();
    }

    @Override
    public void registerTool(LLMToolMetadata LLMToolMetadata) {
        // 实现注册逻辑，这里可以直接调用 ExtensionToolRegistry 的注册方法
        toolMap.computeIfAbsent(LLMToolMetadata.getName(), k -> LLMToolMetadata);
    }

    @Override
    public List<LLM_TOOL_FUNCTION> getRegisteredToolFunctions() {
        // 从 toolMap 获取所有工具，并转换为 LLM 供应商特定的 TOOL_FUNCTION
        return toolMap.values().stream()
            .map(this::toToolFunction)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    }

    // 辅助方法：将 LLMTool 转换为 特定厂商 期望的 ToolFunction 格式
    protected abstract LLM_TOOL_FUNCTION toToolFunction(LLMToolMetadata LLMToolMetadata);

    /**
     * 处理 LLM 工具调用命令。
     * 此方法负责将 LLM 发送的工具调用命令转换为具体的工具执行，并处理其结果。
     *
     * @param env             环境变量。
     * @param originalMessage 原始消息，用于在需要时构建返回消息。
     */
    @Override
    public void processToolCall(TenEnv env, ToolCallOutputBlock toolCallOutputBlock, Message originalMessage) {
        log.info("[{}] 开始处理工具调用. Tool Name: {}, Arguments: {}",
            env.getExtensionName(), toolCallOutputBlock.getToolName(), toolCallOutputBlock.getArgumentsJson());

        LLMToolMetadata LLMToolMetadata = toolMap.get(toolCallOutputBlock.getToolName());
        if (LLMToolMetadata == null) {
            log.warn("[{}] 未注册的工具: {}. 返回错误信息。", env.getExtensionName(), toolCallOutputBlock.getToolName());
        }

        // 构建 CMD_TOOL_CALL 命令
        Command toolCallCommand = GenericCommand.createBuilder(CMD_TOOL_CALL, originalMessage.getId())
            .property(CMD_TOOL_CALL_PROPERTY_NAME, toolCallOutputBlock.getToolName())
            .property(CMD_TOOL_CALL_PROPERTY_ARGUMENTS, toolCallOutputBlock.getArgumentsJson())
            .property(CMD_TOOL_CALL_PROPERTY_TOOL_CALL_ID, toolCallOutputBlock.getId())
            .property(DATA_OUT_PROPERTY_TEXT, originalMessage.getPropertyString(DATA_OUT_PROPERTY_TEXT).orElse(""))
            //.property(MESSAGE_GROUP_TIMESTAMP_NAME, originalMessage.getPropertyLong(MESSAGE_GROUP_TIMESTAMP_NAME)
            //    .orElse(System.currentTimeMillis()))
            .build();

        llmContextManager.onAssistantMsg(createToolCallAssistantMessage(toolCallOutputBlock));

        log.debug("[{}] 发送工具调用命令: name={}, arguments={}, tool_call_id={}", env.getExtensionName(),
            toolCallOutputBlock.getToolName(), toolCallOutputBlock.getArgumentsJson(),
            toolCallOutputBlock.getId());

        // 使用 env.sendAsyncCmd 并处理其 CompletableFuture 回调
        disposables.add(env.submitCommandWithResultHandle(toolCallCommand)
            .toFlowable()
            .subscribe(cmdResult -> toolCallCommandCompletedCallback(
                toolCallOutputBlock,
                toolCallCommand,
                env,
                cmdResult,
                null
            ), throwable -> toolCallCommandCompletedCallback(
                toolCallOutputBlock,
                toolCallCommand,
                env,
                null,
                throwable
            )));
    }

    protected abstract MESSAGE createToolCallAssistantMessage(ToolCallOutputBlock toolCallOutputBlock);

    /**
     * 抽象方法：创建一个 MESSAGE 对象，用于表示带有 toolCallId 的工具消息。
     * 子类需要实现此方法来创建具体的 MESSAGE 类型。
     *
     * @param cmdThrowable cmdThrowable
     * @return 创建的 MESSAGE 对象。
     */
    protected abstract MESSAGE createErrorToolCallMessage(ToolCallOutputBlock toolCallOutputBlock,
        Throwable cmdThrowable);

    protected abstract MESSAGE createToolCallMessage(ToolCallOutputBlock toolCallOutputBlock,
        String result);

    /**
     * 处理工具命令执行的异步结果。
     *
     * @param callOutputBlock 工具调用的唯一ID。
     * @param originalMessage 原始消息，用于在需要时构建返回消息。
     * @param env             环境变量。
     * @param cmdResult      命令执行结果。
     * @param error    命令执行过程中抛出的异常。
     */
    private void toolCallCommandCompletedCallback(ToolCallOutputBlock callOutputBlock, Message originalMessage,
        TenEnv env,
        CommandResult cmdResult, Throwable error) {
        try {
            String errorMsg = null;
            String toolCallContent;
            String assistantMessage;
            boolean secondRound = false;

            if (cmdResult != null) {
                if (cmdResult.isInvalid()) {
                    // 忽略无效的命令结果
                    return;
                }
                if (cmdResult.isSuccess()) {
                    toolCallContent = cmdResult.getPropertyString(CMD_TOOL_PROPERTY_TOOL_CALL_CONTENT).orElse("");
                    assistantMessage = cmdResult.getPropertyString(CMD_TOOL_PROPERTY_ASSISTANT_MESSAGE).orElse("");
                    secondRound = cmdResult.getPropertyBoolean(CMD_TOOL_PROPERTY_SECOND_ROUND).orElse(false);
                    log.info("[{}] 工具调用命令执行成功: toolName={}, toolCallContent={} assistantMessage={}",
                        env.getExtensionName(), callOutputBlock.getToolName(), toolCallContent, assistantMessage);
                    // 将工具执行结果添加到历史
                    if (isNotBlank(toolCallContent)) {
                        MESSAGE toolOutputMsg =
                            createToolCallMessage(callOutputBlock, toolCallContent);
                        llmContextManager.onToolCallMsg(toolOutputMsg);
                    }
                    // 工具执行记录添加到历史
                    if (isNotBlank(assistantMessage)) {
                        llmContextManager.onAssistantMsg(assistantMessage);
                    }

                } else {
                    errorMsg = cmdResult.getErrorMessage();
                }
            }

            if (error != null) {
                errorMsg = "任务执行失败.";
            }
            if (isNotBlank(errorMsg)) {
                log.error("[{}] 工具调用命令执行失败: toolName={} errorMsg={}", env.getExtensionName(), callOutputBlock.getToolName(), errorMsg, error);
                // 将失败结果添加到历史
                MESSAGE toolErrorMsg =
                    createToolCallMessage(callOutputBlock,
                        "工具执行失败: %s".formatted(errorMsg));
                llmContextManager.onToolCallMsg(toolErrorMsg);
            }

            // 收到工具结果后，再次调用LLM
            log.info("[{}] LLM请求工具调用，已收到异步命令结果，开始调用LLM: toolName={}, callOutputBlock={}",
                env.getExtensionName(), callOutputBlock.getToolName(), callOutputBlock);

            if (secondRound) {
                List<MESSAGE> messagesForNextTurn = llmContextManager.getMessagesForLLM();
                List<LLM_TOOL_FUNCTION> registeredToolFunctions = getRegisteredToolFunctions();
                llmStreamAdapter.onRequestLLMAndProcessStream(
                    env,
                    messagesForNextTurn,
                    registeredToolFunctions,
                    cmdResult
                );
            }
        } catch (Exception e) {
            log.error("[{}] 处理工具调用CompletableFuture回调异常: toolName={}, callOutputBlock={}, error={}",
                env.getExtensionName(), callOutputBlock.getToolName(), callOutputBlock, e.getMessage(), e);
            sendErrorResult(env, originalMessage.getId(), originalMessage.getType(),
                originalMessage.getName(), "处理工具回调失败: %s".formatted(e.getMessage()));
        }
    }

    /**
     * 发送错误结果。
     *
     * @param env          环境变量。
     * @param commandId    命令ID。
     * @param type         消息类型。
     * @param name         消息名称。
     * @param errorMessage 错误消息。
     */
    protected abstract void sendErrorResult(TenEnv env, String commandId, MessageType type, String name,
        String errorMessage);
}
