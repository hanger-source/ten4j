package source.hanger.core.extension.system.llm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Collections;

import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolFunction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.JsonObject;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.common.ExtensionConstants;
import source.hanger.core.extension.system.tool.LLMToolService; // [{}] 更新LLMToolService的导入路径
import source.hanger.core.extension.system.tool.ToolMetadata; // [{}] 更新ToolMetadata的导入路径
import source.hanger.core.extension.system.BaseFlushExtension;
import source.hanger.core.extension.system.llm.history.LLMHistoryManager;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message; // [{}] 使用 core 包的 Message
import source.hanger.core.message.MessageType;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand; // [{}] 新增 GenericCommand 导入
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;

import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_END_OF_SEGMENT;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_ROLE;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;

/**
 * LLM基础抽象类
 * 基于ten-framework AI_BASE的AsyncLLMBaseExtension设计
 * <p>
 * 核心特性：
 * 1. 异步处理队列机制 (现在通过 QueueAgent 实现)
 * 2. 流式处理支持 - 支持流式文本输出和中断机制
 * 3. 会话状态管理 - 维护对话历史和上下文
 * 4. 精确的错误处理和监控 (此处为占位符，需实际集成)
 * 5. 工具调用支持 (新增)
 */
@Slf4j
public abstract class BaseLLMExtension extends BaseFlushExtension<GenerationResult> {

    protected final Map<String, Object> sessionState = new java.util.concurrent.ConcurrentHashMap<>();

    // [{}] 新增：LLM工具相关成员变量
    protected LLMToolService llmToolService;
    protected final List<ToolFunction> registeredFunctions = new CopyOnWriteArrayList<>();
    protected final ObjectMapper objectMapper = new ObjectMapper();

    // [{}] 新增：历史记录管理
    protected LLMHistoryManager llmHistoryManager; // [{}] 将历史管理抽离为单独的类

    // [{}] 新增：文本生成处理相关成员变量
    protected final StringBuilder currentLlmResponse = new StringBuilder(); // 用于累积LLM的完整回复
    protected String sentenceFragment = ""; // 用于存储未完成的句子片段

    public BaseLLMExtension() {
        super();
    }

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        super.onConfigure(env, properties);
        // [{}] 初始化LLMToolService
        llmToolService = new LLMToolService(env);

        // [{}] 初始化 LLMHistoryManager
        llmHistoryManager = new LLMHistoryManager(objectMapper, this::getSystemPrompt); // 传入 ObjectMapper 和获取系统提示词的 Supplier

        if (properties.containsKey("max_memory_length")) { // [{}] 允许从配置中读取maxHistory
            llmHistoryManager.setMaxHistory((int) properties.get("max_memory_length"));
        }
    }

    @Override
    public void onInit(TenEnv env) {
        super.onInit(env);
        log.info("[{}] LLM扩展初始化阶段", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[{}] LLM扩展启动阶段", env.getExtensionName());
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
        log.info("[{}] LLM扩展停止阶段", env.getExtensionName());
    }

    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
        log.info("[{}] LLM扩展清理阶段", env.getExtensionName());
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        if (!isRunning()) { // 使用父类的 isRunning() 方法
            log.warn("[{}] LLM扩展未运行，忽略命令, commandName={}",
                env.getExtensionName(), command.getName());
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            String commandName = command.getName();
            switch (commandName) {
                case ExtensionConstants.CMD_CHAT_COMPLETION_CALL:
                    handleChatCompletionCall(env, command);
                    break;
                case ExtensionConstants.CMD_IN_ON_USER_JOINED:
                    onUserJoined(env, command);
                    CommandResult userJoinedResult = CommandResult.success(command, "User joined.");
                    env.sendResult(userJoinedResult);
                    break;
                case ExtensionConstants.CMD_IN_ON_USER_LEFT:
                    onUserLeft(env, command);
                    CommandResult userLeftResult = CommandResult.success(command, "User left.");
                    env.sendResult(userLeftResult);
                    break;
                // [{}] 处理工具注册命令
                case ExtensionConstants.CMD_TOOL_REGISTER:
                    try {
                        String toolJson = command.getProperty(ExtensionConstants.CMD_PROPERTY_TOOL, String.class);
                        ToolMetadata toolMetadata = objectMapper.readValue(toolJson, ToolMetadata.class);
                        ToolFunction toolFunction = toDashScopeFunction(toolMetadata);
                        registeredFunctions.add(toolFunction);
                        log.info("[{}] 注册工具成功: name={}",env.getExtensionName(), toolMetadata.getName());
                        env.sendResult(CommandResult.success(command, "Tool registered successfully."));
                    } catch (Exception e) {
                        log.error("[{}] 注册工具失败: {}",env.getExtensionName(), e.getMessage(), e);
                        sendErrorResult(env, command, "Failed to register tool: %s".formatted(e.getMessage()));
                    }
                    break;
                default:
                    // 将其他未处理的命令传递给父类
                    super.onCmd(env, command);
                    break;
            }
            long duration = System.currentTimeMillis() - startTime;
            log.debug("[{}] LLM扩展命令 {} 处理耗时: {} ms",env.getExtensionName(), commandName, duration);
        } catch (Exception e) {
            log.error("[{}] LLM扩展命令处理异常, commandName={}",env.getExtensionName(),
                env.getExtensionName(), command.getName(), e);
            sendErrorResult(env, command, "LLM命令处理异常: %s".formatted(e.getMessage()));
        }
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage data) {
        if (!isRunning()) { // 使用父类的 isRunning() 方法
            log.warn("[{}] LLM扩展未运行，忽略数据: dataId={}",
                env.getExtensionName(), data.getId());
            return;
        }
        if (!data.getPropertyBool("is_final").orElse(false)) {
            log.info("[{}] LLM扩展收到非最终数据: text={}", env.getExtensionName(), data.getProperty("text"));
            return;
        }
        // onRequestLLM 现在返回 Flowable<GenerationResult>，并接收工具列表
        // 这里需要确保将已注册的工具传递给LLM请求
        // [{}] 将用户输入添加到历史
        String inputText = (String)data.getProperty("text");
        if (inputText != null && !inputText.isEmpty()) {
            llmHistoryManager.onMsg("user", inputText); // [{}] 调用 LLMHistoryManager
        }
        streamProcessor.onNext(new StreamPayload<>(onRequestLLM(env, llmHistoryManager.getMessagesForLLM(), registeredFunctions), data)); // [{}] 更新onRequestLLM的调用
    }

    @Override
    protected void handleStreamItem(GenerationResult item, Message originalMessage, TenEnv env) { // [{}] 使用 core 包的 Message
        // [{}] 在BaseLLMExtension中处理工具调用逻辑
        if (item != null && item.getOutput() != null && item.getOutput().getChoices() != null
            && !item.getOutput().getChoices().isEmpty()) {
            // 检查是否有工具调用
            if (item.getOutput().getChoices().get(0).getMessage().getToolCalls() != null &&
                !item.getOutput().getChoices().get(0).getMessage().getToolCalls().isEmpty()) {

                // [{}] 避免直接引用不存在的 com.alibaba.dashscope.common.ToolCall，先获取为Object再转换
                Object rawToolCallObject = item.getOutput().getChoices().get(0).getMessage().getToolCalls().get(0);
                // 先转换为 Map<String, Object>，再转换为 ToolCallFunction，避免直接从SDK类型转换的问题
                Map<String, Object> rawToolCallMap = objectMapper.convertValue(rawToolCallObject,
                    new TypeReference<>() {});
                com.alibaba.dashscope.tools.ToolCallFunction toolCall = objectMapper.convertValue(rawToolCallMap, com.alibaba.dashscope.tools.ToolCallFunction.class);
                String functionName = toolCall.getFunction().getName();
                String arguments = toolCall.getFunction().getArguments();
                String toolCallId = toolCall.getId();
                if (toolCallId == null || toolCallId.isEmpty()) {
                    return;
                }

                // 构建 CMD_TOOL_CALL 命令
                Command toolCallCommand = GenericCommand.create(ExtensionConstants.CMD_TOOL_CALL);
                toolCallCommand.setProperty("name", functionName);
                toolCallCommand.setProperty("arguments", arguments);
                toolCallCommand.setProperty("tool_call_id", toolCallId);
                toolCallCommand.setParentCommandId(originalMessage.getId()); // 关联原始消息

                // [{}] 将 tool_calls 信息添加到历史 (作为 assistant 角色)
                com.alibaba.dashscope.common.Message toolCallMessage = com.alibaba.dashscope.common.Message.builder()
                        .role(Role.ASSISTANT.getValue())
                        .toolCalls(Collections.singletonList(toolCall))
                        .build();
                llmHistoryManager.onOtherMsg(toolCallMessage); // 直接传递 Message 对象

                // [{}] 使用 env.sendAsyncCmd 并处理其 CompletableFuture 回调
                env.sendAsyncCmd(toolCallCommand)
                    .whenComplete((cmdResult, cmdThrowable) -> {
                        env.postTask(() -> { // 确保在 Runloop 线程中执行后续操作
                            try {
                                if (cmdThrowable != null) {
                                    log.error("[{}] 工具调用命令执行失败: toolName={}, toolCallId={}, error={}",env.getExtensionName(),
                                        functionName, toolCallId, cmdThrowable.getMessage(), cmdThrowable);
                                    // 将失败结果添加到历史
                                    com.alibaba.dashscope.common.Message toolErrorMsg = com.alibaba.dashscope.common.Message.builder()
                                        .role(Role.TOOL.getValue())
                                        .content("工具执行失败: %s".formatted(cmdThrowable.getMessage()))
                                        .toolCallId(toolCallId)
                                        .build();
                                    llmHistoryManager.onOtherMsg(toolErrorMsg); // 直接传递 Message 对象
                                } else if (cmdResult != null && cmdResult.isSuccess()) {
                                    String toolResultJson = cmdResult.getProperty(ExtensionConstants.CMD_PROPERTY_RESULT, String.class);
                                    log.info("[{}] 工具调用命令执行成功: toolName={}, toolCallId={}, result={}",env.getExtensionName(),
                                        functionName, toolCallId, toolResultJson);
                                    // 将工具执行结果添加到历史
                                    com.alibaba.dashscope.common.Message toolOutputMsg = com.alibaba.dashscope.common.Message.builder()
                                        .role(Role.TOOL.getValue())
                                        .content(toolResultJson)
                                        .toolCallId(toolCallId)
                                        .build();
                                    llmHistoryManager.onOtherMsg(toolOutputMsg); // 直接传递 Message 对象
                                } else {
                                    //String errorMsg = cmdResult != null ? cmdResult.getMessage() : "未知错误";
                                    String errorMsg = "";
                                    log.error("[{}] 工具调用命令执行失败（非异常）: toolName={}, toolCallId={}, message={}",env.getExtensionName(),
                                        functionName, toolCallId, errorMsg);
                                    // 将失败结果添加到历史
                                    com.alibaba.dashscope.common.Message toolErrorMsg = com.alibaba.dashscope.common.Message.builder()
                                        .role(Role.TOOL.getValue())
                                        .content("工具执行失败: %s".formatted(errorMsg))
                                        .toolCallId(toolCallId)
                                        .build();
                                    llmHistoryManager.onOtherMsg(toolErrorMsg); // 直接传递 Message 对象
                                }
                                // 收到工具结果后，再次调用LLM
                                // 将其通过 streamProcessor 处理，以确保流式输出和取消机制正常工作
                                streamProcessor.onNext(new StreamPayload<>(
                                    onRequestLLM(env, llmHistoryManager.getMessagesForLLM(), registeredFunctions),
                                    originalMessage // 使用原始消息作为 payload 的 data
                                ));
                            } catch (Exception e) {
                                log.error("[{}] 处理工具调用CompletableFuture回调异常: toolName={}, toolCallId={}, error={}",env.getExtensionName(),
                                    functionName, toolCallId, e.getMessage(), e);
                                sendErrorResult(env, originalMessage.getId(), originalMessage.getType(), originalMessage.getName(), "处理工具回调失败: %s".formatted(e.getMessage()));
                            }
                        });
                    });

                log.info("[{}] LLM请求工具调用，已发送异步命令: toolName={}, arguments={}, toolCallId={}", env.getExtensionName(),functionName, arguments, toolCallId);
                return; // 处理完工具调用后，不再进行文本生成处理
            }
        }
        // 如果没有工具调用，则交给子类处理文本生成结果
        processLlmStreamTextOutput(item, originalMessage, env);
    }

    @Override
    protected void onCancelFlush(TenEnv env) {
        onCancelLLM(env);
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        super.onAudioFrame(env, audioFrame);
        if (!isRunning()) { // [{}] 修复日志参数
            log.warn("[{}] LLM扩展未运行，忽略音频帧: frameId={}",
                env.getExtensionName(), audioFrame.getId());
            return;
        }

        log.debug("[{}] LLM扩展收到音频帧: frameId={}",
            env.getExtensionName(), audioFrame.getId());
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) {
        super.onVideoFrame(env, videoFrame);
        if (!isRunning()) { // [{}] 修复日志参数
            log.warn("[{}] LLM扩展未运行，忽略视频帧: frameId={}", env.getExtensionName(), videoFrame.getId());
            return;
        }

        if (interrupted.get()) { // [{}] 修复日志参数
            log.warn("[{}] 当前扩展未运行或已中断，丢弃消息", env.getExtensionName());
            return;
        }

        log.debug("[{}] LLM扩展收到视频帧: frameId={}",
            env.getExtensionName(), videoFrame.getId());
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) {
        // [{}] 移除与工具执行结果相关的逻辑，因为这些结果现在由sendAsyncCmd的CompletableFuture回调处理。
        // 这里只处理非工具执行结果的CommandResult，或者作为通用错误捕获。
        log.warn("[{}] LLM扩展 收到未处理的 CommandResult: {}. OriginalCommandId: {}", env.getExtensionName(),
            commandResult.getId(), commandResult.getOriginalCommandId());
        super.onCmdResult(env, commandResult);
    }

    private void handleChatCompletionCall(TenEnv env, Command command) {
        try {
            Map<String, Object> args = (Map<String, Object>)command.getProperty("arguments");
            if (args == null) {
                throw new IllegalArgumentException("缺少聊天完成参数");
            }

            // [{}] 将用户输入添加到历史
            String inputText = (String) args.get("text"); // 假设chat_completion_call的arguments中包含text
            if (inputText != null && !inputText.isEmpty()) {
                llmHistoryManager.onMsg("user", inputText); // [{}] 调用 LLMHistoryManager
            }

            onCallChatCompletion(env, command, llmHistoryManager.getMessagesForLLM(), registeredFunctions); // [{}] 更新onCallChatCompletion的调用

        } catch (Exception e) {
            log.error("[{}] 聊天完成调用处理失败", env.getExtensionName(), e);
            sendErrorResult(env, command, "聊天完成调用失败: %s".formatted(e.getMessage()));
        }
    }

    protected void onUserJoined(TenEnv env, Command command) {
        log.info("[{}] LLM扩展收到用户加入事件", env.getExtensionName());
        // Python实现中这里会发送 greeting 消息，Java版本目前只返回OK
        String greeting = String.valueOf(env.getProperty("greeting").orElse(""));
        if (greeting != null && !greeting.isEmpty()) {
            try {
                llmHistoryManager.onMsg("assistant", greeting); // [{}] 调用 LLMHistoryManager
                sendTextOutput(env, command, greeting, true);
                log.info("[{}] Greeting [{}] sent to user.",env.getExtensionName(), greeting);
            } catch (Exception e) {
                log.error("[{}] Failed to send greeting [{}], error: {}",env.getExtensionName(), greeting, e.getMessage(), e);
            }
        }
        flushInputItems(env, command);
    }

    protected void onUserLeft(TenEnv env, Command command) {
        log.info("[{}] LLM扩展收到用户离开事件", env.getExtensionName());
        flushInputItems(env, command);
    }

    protected void sendTextOutput(TenEnv env, Message originalMessage, String text, boolean endOfSegment) { // [{}] 使用 core 包的 Message
        try {
            DataMessage outputData = DataMessage.create(ExtensionConstants.LLM_DATA_OUT_NAME);
            outputData.setId(originalMessage.getId()); // 使用原始消息的ID
            outputData.setProperty(DATA_OUT_PROPERTY_TEXT, text);
            outputData.setProperty(DATA_OUT_PROPERTY_ROLE, "assistant");
            outputData.setProperty(DATA_OUT_PROPERTY_END_OF_SEGMENT, endOfSegment);
            outputData.setProperty("extension_name", env.getExtensionName());
            outputData.setProperty("group_timestamp", originalMessage.getTimestamp());

            env.sendMessage(outputData);
            log.debug("[{}] LLM文本输出发送成功: text={}, endOfSegment={}",env.getExtensionName(),
                text, endOfSegment);
        } catch (Exception e) {
            log.error("[{}] LLM文本输出发送异常: {}", env.getExtensionName(), e.getMessage(), e);
        }
    }

    protected void sendErrorResult(TenEnv env, Command command, String errorMessage) {
        CommandResult errorResult = CommandResult.fail(command, errorMessage);
        env.sendResult(errorResult);
    }

    protected void sendErrorResult(TenEnv env, String messageId, MessageType messageType, String messageName,
        String errorMessage) {
        String finalMessageId = (messageId != null && !messageId.isEmpty()) ? messageId
            : MessageUtils.generateUniqueId();
        CommandResult errorResult = CommandResult.fail(finalMessageId, messageType, messageName, errorMessage);
        env.sendResult(errorResult);
    }

    protected abstract void onCallChatCompletion(TenEnv env, Command originalCommand, List<com.alibaba.dashscope.common.Message> messages, List<ToolFunction> tools); // [{}] 修改onCallChatCompletion的签名

    // [{}] 修改onRequestLLM的签名，使其接收工具列表
    protected abstract Flowable<GenerationResult> onRequestLLM(TenEnv env, List<com.alibaba.dashscope.common.Message> messages, List<ToolFunction> tools);

    // [{}] 新增：处理纯文本流式输出的抽象方法
    protected abstract void processLlmStreamTextOutput(GenerationResult result, Message originalMessage, TenEnv env); // [{}] 使用 core 包的 Message

    protected abstract void onCancelLLM(TenEnv env);

    // [{}] 将ToolMetadata转换为DashScope API期望的ToolFunction格式
    protected ToolFunction toDashScopeFunction(ToolMetadata toolMetadata) {
        // 构建 FunctionDefinition
        FunctionDefinition functionDefinition = FunctionDefinition.builder()
                .name(toolMetadata.getName())
                .description(toolMetadata.getDescription())
                // parameters 需要是 JsonObject
                .parameters(convertParametersToJsonObject(toolMetadata.getParameters()))
                .build();

        // 构建 ToolFunction
        return ToolFunction.builder()
                .function(functionDefinition)
                .build();
    }

    // [{}] 将List<ToolParameter>转换为JsonObject
    protected JsonObject convertParametersToJsonObject(List<ToolMetadata.ToolParameter> parameters) {
        JsonObject schemaObject = new JsonObject();
        schemaObject.addProperty("type", "object");

        JsonObject propertiesObject = new JsonObject();
        List<String> requiredList = new ArrayList<>();

        for (ToolMetadata.ToolParameter param : parameters) {
            JsonObject paramProps = new JsonObject();
            paramProps.addProperty("type", param.getType());
            paramProps.addProperty("description", param.getDescription());
            propertiesObject.add(param.getName(), paramProps);
            if (param.isRequired()) {
                requiredList.add(param.getName());
            }
        }
        schemaObject.add("properties", propertiesObject);

        if (!requiredList.isEmpty()) {
            // 将 requiredList 转换为 JsonArray
            com.google.gson.JsonArray jsonArray = new com.google.gson.JsonArray();
            for (String requiredParam : requiredList) {
                jsonArray.add(requiredParam);
            }
            schemaObject.add("required", jsonArray);
        }
        return schemaObject;
    }

    // [{}] 新增抽象方法：获取子类定义的系统提示词
    protected abstract String getSystemPrompt();
}