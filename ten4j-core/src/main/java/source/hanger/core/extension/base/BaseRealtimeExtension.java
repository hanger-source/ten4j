package source.hanger.core.extension.base;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.common.ExtensionConstants;
import source.hanger.core.extension.system.BaseFlushExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;

/**
 * Realtime API 扩展的抽象基类。
 * 提供处理 Realtime API 事件和消息的基础结构，包括文本输入、音频输入和取消流。
 * @param <REALTIME_EVENT> Realtime API 事件的类型。
 */
@Slf4j
public abstract class BaseRealtimeExtension<REALTIME_EVENT> extends BaseFlushExtension<REALTIME_EVENT> {

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[{}] Realtime 扩展启动", env.getExtensionName());
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
        log.info("[{}] Realtime 扩展停止", env.getExtensionName());
    }

    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
        log.info("[{}] Realtime 扩展清理", env.getExtensionName());
    }

    /**
     * 当接收到数据消息时调用。
     * 子类应该实现此方法以将文本数据发送到 Realtime API。
     */
    @Override
    public void onDataMessage(TenEnv env, DataMessage data) {
        if (!isRunning()) {
            log.warn("[{}] Realtime 扩展未运行，忽略数据: dataId={}", env.getExtensionName(), data.getId());
            return;
        }
        if (interrupted.get()) {
            log.warn("[{}] 当前扩展已中断，丢弃数据消息", env.getExtensionName());
            return;
        }
        String text = data.getPropertyString(ExtensionConstants.DATA_OUT_PROPERTY_TEXT).orElse("");
        if (!text.isEmpty()) {
            onSendTextToRealtime(env, text, data);
        } else {
            log.warn("[{}] 收到空文本数据消息，忽略", env.getExtensionName());
        }
    }

    /**
     * 当接收到音频帧时调用。
     * 子类应该实现此方法以将音频数据发送到 Realtime API。
     */
    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        if (!isRunning()) {
            log.warn("[{}] Realtime 扩展未运行，忽略音频帧: frameId={}", env.getExtensionName(), audioFrame.getId());
            return;
        }
        if (interrupted.get()) {
            log.warn("[{}] 当前扩展已中断，丢弃音频帧", env.getExtensionName());
            return;
        }
        onSendAudioToRealtime(env, audioFrame.getBuf(), audioFrame);
    }

    /**
     * 处理从 Realtime API 接收到的 JSON 事件。
     * 子类将根据事件类型分派处理。
     */
    @Override
    protected void handleStreamItem(REALTIME_EVENT item, Message originalMessage, TenEnv env) {
        // 在子类中实现具体的事件分派逻辑
        log.debug("[{}] 收到 Realtime API 事件: {}", env.getExtensionName(), item.toString());
        processRealtimeEvent(env, item, originalMessage);
    }

    /**
     * 抽象方法：发送文本到 Realtime API。
     * 子类必须实现此方法以将文本数据发送到底层的 Realtime API 客户端。
     *
     * @param env             当前的 TenEnv 环境
     * @param text            要发送的文本内容
     * @param originalMessage 原始消息（用于关联）
     */
    protected abstract void onSendTextToRealtime(TenEnv env, String text, Message originalMessage);

    /**
     * 抽象方法：发送音频数据到 Realtime API。
     * 子类必须实现此方法以将音频数据发送到底层的 Realtime API 客户端。
     *
     * @param env             当前的 TenEnv 环境
     * @param audioData       音频数据字节数组
     * @param originalMessage 原始消息（用于关联）
     */
    protected abstract void onSendAudioToRealtime(TenEnv env, byte[] audioData, Message originalMessage);

    /**
     * 抽象方法：处理来自 Realtime API 的 JSON 事件。
     * 子类必须实现此方法以解析并处理不同类型的实时事件（如文本、音频、工具调用等）。
     *
     * @param env             当前的 TenEnv 环境
     * @param event           从 Realtime API 接收到的 JSON 事件
     * @param originalMessage 原始消息（用于关联，如果适用）
     */
    protected abstract void processRealtimeEvent(TenEnv env, REALTIME_EVENT event, Message originalMessage);

    /**
     * 抽象方法：取消 Realtime API 操作。
     * 子类必须实现此方法以停止底层的 Realtime API 连接或请求。
     */
    @Override
    protected void onCancelFlush(TenEnv env) {
        onCancelRealtime(env);
    }

    protected abstract void onCancelRealtime(TenEnv env);

    @Override
    public void onCmd(TenEnv env, Command command) {
        // Realtime 扩展可能需要处理特定的命令，例如用户加入/离开或特定的会话管理命令
        // 类似于 LLM 和 ASR 扩展，将 CMD_IN_FLUSH 传递给父类处理
        if (ExtensionConstants.CMD_IN_FLUSH.equals(command.getName())) {
            super.onCmd(env, command);
        } else {
            // 处理其他 Realtime 相关的命令
            handleRealtimeCommand(env, command);
        }
    }

    /**
     * 抽象方法：处理 Realtime API 特定的命令。
     *
     * @param env     当前的 TenEnv 环境
     * @param command 接收到的命令
     */
    protected abstract void handleRealtimeCommand(TenEnv env, Command command);


    protected void sendAudioTranscriptionText(TenEnv env, String eventId, String text, boolean endOfSegment) {
        sendTextOutput(env, eventId, eventId, text, endOfSegment, "user");
    }

    protected void sendTextOutput(TenEnv env, String eventId, String responseId, String text, boolean endOfSegment) {
        sendTextOutput(env, eventId, responseId, text, endOfSegment, "assistant");
    }
    /**
     * 发送文本输出。
     *
     * @param env             当前的TenEnv环境
     * @param text            要发送的文本内容
     * @param endOfSegment    是否是片段的结束
     */
    protected void sendTextOutput(TenEnv env, String eventId, String groupId, String text, boolean endOfSegment, String role) {
        try {
            DataMessage outputData = DataMessage.create(ExtensionConstants.TEXT_DATA_OUT_NAME); // Define this
            // constant
            outputData.setId(eventId);
            outputData.setProperty(ExtensionConstants.DATA_OUT_PROPERTY_TEXT, text);
            outputData.setProperty(ExtensionConstants.DATA_OUT_PROPERTY_END_OF_SEGMENT, endOfSegment);
            outputData.setProperty("extension_name", env.getExtensionName());
            outputData.setProperty("group_id", groupId);
            outputData.setProperty(ExtensionConstants.DATA_OUT_PROPERTY_ROLE, role); // Assuming assistant role
            // for output

            env.sendMessage(outputData);
            log.debug("[{}] Realtime文本输出发送成功: text={}, endOfSegment={}", env.getExtensionName(), text,
                endOfSegment);
        } catch (Exception e) {
            log.error("[{}] Realtime文本输出发送异常: ", env.getExtensionName(), e);
        }
    }

    /**
     * 发送音频输出。
     *
     * @param env              当前的TenEnv环境
     * @param audioData        音频数据字节数组
     * @param sampleRate       采样率
     * @param bytesPerSample   每样本字节数
     * @param numberOfChannels 通道数
     */
    protected void sendAudioOutput(TenEnv env, String eventId, String responseId, byte[] audioData,
        int sampleRate, int bytesPerSample, int numberOfChannels) {
        try {
            AudioFrameMessage audioFrame = AudioFrameMessage.create("pcm_frame");
            audioFrame.setId(eventId);
            audioFrame.setSampleRate(sampleRate);
            audioFrame.setBytesPerSample(bytesPerSample);
            audioFrame.setNumberOfChannel(numberOfChannels);
            audioFrame.setSamplesPerChannel(audioData.length / (bytesPerSample * numberOfChannels));
            audioFrame.setBuf(audioData);
            audioFrame.setType(MessageType.AUDIO_FRAME);
            audioFrame.setProperty("extension_name", env.getExtensionName());
            audioFrame.setProperty("group_id", responseId);

            env.sendMessage(audioFrame);
            log.debug("[{}] Realtime音频帧发送成功: size={}", env.getExtensionName(), audioData.length);
        } catch (Exception e) {
            log.error("[{}] Realtime音频帧发送异常: ", env.getExtensionName(), e);
        }
    }

    // 发送错误结果
    @Override
    protected void sendErrorResult(TenEnv env, String messageId, MessageType messageType, String messageName,
        String errorMessage) {
        String finalMessageId = (messageId != null && !messageId.isEmpty()) ? messageId
            : MessageUtils.generateUniqueId();
        CommandResult errorResult = CommandResult.fail(finalMessageId, messageType, messageName, errorMessage);
        env.sendResult(errorResult);
        log.error("[{}] Realtime Error [{}]: {}", env.getExtensionName(), messageName, errorMessage);
    }

    protected void sendErrorResult(TenEnv env, Command command, String errorMessage) {
        CommandResult errorResult = CommandResult.fail(command, errorMessage);
        env.sendResult(errorResult);
    }
}
