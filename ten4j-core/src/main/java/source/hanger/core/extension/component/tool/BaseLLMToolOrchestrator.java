package source.hanger.core.extension.component.tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.tool.LLMToolMetadata;
import source.hanger.core.extension.base.tool.LLMToolResult;
import source.hanger.core.extension.base.tool.LLMToolResult.Noop;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.extension.component.llm.LLMStreamAdapter;
import source.hanger.core.extension.component.llm.ToolCallOutputBlock;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;

import static source.hanger.core.common.ExtensionConstants.CMD_PROPERTY_RESULT;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL_PROPERTY_ARGUMENTS;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL_PROPERTY_NAME;
import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL_PROPERTY_TOOL_CALL_ID;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, LLMToolMetadata> toolMap = new HashMap<>();
    private final LLMStreamAdapter<MESSAGE, LLM_TOOL_FUNCTION> llmStreamAdapter;

    public BaseLLMToolOrchestrator(
        LLMContextManager<MESSAGE> llmContextManager,
        LLMStreamAdapter<MESSAGE, LLM_TOOL_FUNCTION> llmStreamAdapter) {
        this.llmContextManager = llmContextManager;
        this.llmStreamAdapter = llmStreamAdapter;
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
            .build();

        llmContextManager.onAssistantMsg(createToolCallAssistantMessage(toolCallOutputBlock));

        log.debug("[{}] 发送工具调用命令: name={}, arguments={}, tool_call_id={}", env.getExtensionName(),
            toolCallOutputBlock.getToolName(), toolCallOutputBlock.getArgumentsJson(),
            toolCallOutputBlock.getId());

        // 使用 env.sendAsyncCmd 并处理其 CompletableFuture 回调
        env.sendAsyncCmd(toolCallCommand)
            .toCompletedFuture() // 获取 CompletableFuture<List<CommandResult>>
            .whenComplete((cmdResults, cmdThrowable) -> toolCallCommandCompletedCallback(
                toolCallOutputBlock,
                originalMessage,
                env,
                cmdResults,
                cmdThrowable
            ));
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
     * @param cmdResults      命令执行结果列表。
     * @param cmdThrowable    命令执行过程中抛出的异常。
     */
    private void toolCallCommandCompletedCallback(ToolCallOutputBlock callOutputBlock, Message originalMessage,
        TenEnv env,
        List<CommandResult> cmdResults, Throwable cmdThrowable) {
        try {
            LLMToolResult llmToolResult = null;
            if (cmdThrowable != null) {
                log.error("[{}] 工具调用命令执行失败: toolName={}, error={}",
                    env.getExtensionName(), callOutputBlock.getToolName(), cmdThrowable.getMessage(), cmdThrowable);
                // 将失败结果添加到历史
                MESSAGE toolErrorMsg = createErrorToolCallMessage(callOutputBlock, cmdThrowable);
                llmContextManager.onToolCallMsg(toolErrorMsg);
            } else if (cmdResults != null && !cmdResults.isEmpty()) {
                // 找到最后一个 isCompleted=true 的 CommandResult，或者最后一个结果
                CommandResult finalCmdResult = cmdResults.stream()
                    .filter(CommandResult::getIsCompleted) // 优先找 isCompleted=true 的结果
                    .findFirst() // 如果有多个，取第一个
                    .orElse(cmdResults.getLast()); // 如果没有 isCompleted=true，则取列表的最后一个

                if (finalCmdResult.isSuccess()) {
                    String toolResultJson =
                        finalCmdResult.getPropertyString(CMD_PROPERTY_RESULT).orElse("");
                    log.info("[{}] 工具调用命令执行成功: toolName={}, result={}",
                        env.getExtensionName(), callOutputBlock.getToolName(), toolResultJson);

                    llmToolResult = objectMapper.readValue(toolResultJson, LLMToolResult.class);
                    // 将工具执行结果添加到历史
                    MESSAGE toolOutputMsg =
                        createToolCallMessage(callOutputBlock, toolResultJson);
                    llmContextManager.onToolCallMsg(toolOutputMsg);
                } else {
                    String errorMsg = finalCmdResult.getDetail() != null ? finalCmdResult.getDetail()
                        : "未知错误";
                    log.error("[{}] 工具调用命令执行失败（非异常）: toolName={}, message={}",
                        env.getExtensionName(), callOutputBlock.getToolName(), errorMsg);
                    // 将失败结果添加到历史
                    MESSAGE toolErrorMsg =
                        createToolCallMessage(callOutputBlock,
                            "工具执行失败: %s".formatted(errorMsg));
                    llmContextManager.onToolCallMsg(toolErrorMsg);
                }
            } else {
                // 这种情况通常不应该发生，因为即使没有成功的 CommandResult，也应该有一个结果列表
                log.error("[{}] 工具调用命令执行完成但未返回任何结果: toolName={}",
                    env.getExtensionName(), callOutputBlock.getToolName());
                sendErrorResult(env, originalMessage.getId(), originalMessage.getType(),
                    originalMessage.getName(), "工具调用未返回任何结果");
            }
            // 收到工具结果后，再次调用LLM
            log.info("[{}] LLM请求工具调用，已收到异步命令结果，开始调用LLM: toolName={}, callOutputBlock={}",
                env.getExtensionName(), callOutputBlock.getToolName(), callOutputBlock);

            if (!(llmToolResult instanceof Noop)) {
                List<MESSAGE> messagesForNextTurn = llmContextManager.getMessagesForLLM();
                List<LLM_TOOL_FUNCTION> registeredToolFunctions = getRegisteredToolFunctions();
                llmStreamAdapter.onRequestLLMAndProcessStream(
                    env,
                    messagesForNextTurn,
                    registeredToolFunctions,
                    originalMessage
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
