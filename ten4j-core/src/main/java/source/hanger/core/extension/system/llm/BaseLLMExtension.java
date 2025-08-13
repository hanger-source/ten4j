package source.hanger.core.extension.system.llm;

import java.util.Map;

import com.alibaba.dashscope.aigc.generation.GenerationResult;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.system.BaseFlushExtension;
import source.hanger.core.extension.system.ExtensionConstants;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;

/**
 * LLM基础抽象类
 * 基于ten-framework AI_BASE的AsyncLLMBaseExtension设计
 * <p>
 * 核心特性：
 * 1. 异步处理队列机制 (现在通过 QueueAgent 实现)
 * 2. 流式处理支持 - 支持流式文本输出和中断机制
 * 3. 会话状态管理 - 维护对话历史和上下文
 * 4. 精确的错误处理和监控 (此处为占位符，需实际集成)
 */
@Slf4j
public abstract class BaseLLMExtension extends BaseFlushExtension<GenerationResult> {

    private final Map<String, Object> sessionState = new java.util.concurrent.ConcurrentHashMap<>();

    public BaseLLMExtension() {
        super();
    }

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        super.onConfigure(env, properties);
    }

    @Override
    public void onInit(TenEnv env) {
        super.onInit(env);
        log.info("LLM扩展初始化阶段: extensionName={}", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        // log.info("LLM扩展启动阶段: extensionName={}", env.getExtensionName());
        // isRunning = true;
        // interrupted.set(false);
        // disposable = generateDisposable(env);
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
    }

    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略命令: extensionName={}, commandName={}",
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
                default:
                    // 将其他未处理的命令传递给父类
                    super.onCmd(env, command);
                    break;
            }
            long duration = System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            log.error("LLM扩展命令处理异常: extensionName={}, commandName={}",
                env.getExtensionName(), command.getName(), e);
            sendErrorResult(env, command, "LLM命令处理异常: %s".formatted(e.getMessage()));
        }
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage data) {
        if (!isRunning) {
            log.warn("[{}] LLM扩展未运行，忽略数据: dataId={}",
                env.getExtensionName(), data.getId());
            return;
        }
        // onRequestLLM 现在返回 Flowable<GenerationResult>
        streamProcessor.onNext(new StreamPayload<>(onRequestLLM(env, data), data));
    }

    @Override
    protected void handleStreamItem(GenerationResult item, Message originalMessage, TenEnv env) {
        // 交给子类处理原始的 GenerationResult
        processLlmGenerationResult(item, originalMessage, env);
    }

    @Override
    protected void onCancelFlush(TenEnv env) {
        onCancelLLM(env);
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        super.onAudioFrame(env, audioFrame);
        if (!isRunning) {
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
        if (!isRunning) {
            log.warn("[{}] LLM扩展未运行，忽略视频帧: frameId={}", env.getExtensionName(), videoFrame.getId());
            return;
        }

        if (interrupted.get()) {
            log.warn("[{}] 当前扩展未运行或已中断，丢弃消息", env.getExtensionName());
            return;
        }

        log.debug("[{}] LLM扩展收到视频帧: frameId={}",
            env.getExtensionName(), videoFrame.getId());
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) {
        super.onCmdResult(env, commandResult);
        log.warn("[{}] LLM扩展 收到未处理的 CommandResult: {}. OriginalCommandId: {}", env.getExtensionName(),
            commandResult.getId(), commandResult.getOriginalCommandId());
    }

    private void handleChatCompletionCall(TenEnv env, Command command) {
        try {
            Map<String, Object> args = (Map<String, Object>)command.getProperty("arguments");
            if (args == null) {
                throw new IllegalArgumentException("缺少聊天完成参数");
            }

            onCallChatCompletion(env, command, args);

        } catch (Exception e) {
            log.error("[{}] 聊天完成调用处理失败", env.getExtensionName(), e);
            sendErrorResult(env, command, "聊天完成调用失败: %s".formatted(e.getMessage()));
        }
    }

    protected void onUserJoined(TenEnv env, Command command) {
        log.info("[{}] LLM扩展收到用户加入事件", env.getExtensionName());
        flushInputItems(env, command);
    }

    protected void onUserLeft(TenEnv env, Command command) {
        log.info("[{}] LLM扩展收到用户离开事件", env.getExtensionName());
        flushInputItems(env, command);
    }

    protected void sendTextOutput(TenEnv env, Message originalMessage, String text, boolean endOfSegment) {
        try {
            DataMessage outputData = DataMessage.create(ExtensionConstants.LLM_DATA_OUT_NAME);
            outputData.setId(originalMessage.getId()); // 使用原始消息的ID
            outputData.setProperty(ExtensionConstants.DATA_OUT_PROPERTY_TEXT, text);
            outputData.setProperty("role", "assistant");
            outputData.setProperty(ExtensionConstants.DATA_OUT_PROPERTY_END_OF_SEGMENT, endOfSegment);
            outputData.setProperty("extension_name", env.getExtensionName());
            outputData.setProperty("group_timestamp", originalMessage.getTimestamp());

            env.sendMessage(outputData);
            log.debug("[{}] LLM文本输出发送成功: text={}, endOfSegment={}",
                env.getExtensionName(), text, endOfSegment);
        } catch (Exception e) {
            log.error("[{}] LLM文本输出发送异常: ", env.getExtensionName(), e);
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

    protected abstract void onCallChatCompletion(TenEnv env, Command originalCommand, Map<String, Object> args);

    protected abstract Flowable<GenerationResult> onRequestLLM(TenEnv env, DataMessage data);

    protected abstract void processLlmGenerationResult(GenerationResult result, Message originalMessage, TenEnv env);

    protected abstract void onCancelLLM(TenEnv env);
}