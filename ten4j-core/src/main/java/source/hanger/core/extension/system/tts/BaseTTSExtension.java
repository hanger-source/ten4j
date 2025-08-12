package source.hanger.core.extension.system.tts;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.BaseExtension;
import source.hanger.core.extension.system.ExtensionConstants;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;
import source.hanger.core.util.QueueAgent;

/**
 * TTS基础抽象类
 * 基于ten-framework AI_BASE的tts.py设计
 *
 * 核心特性：
 * 1. 异步处理队列机制 (通过 QueueAgent 实现)
 * 2. 音频数据流式输出
 * 3. 输入数据处理
 */
@Slf4j
public abstract class BaseTTSExtension extends BaseExtension {

    protected final AtomicBoolean interrupted = new AtomicBoolean(false);
    protected boolean isRunning = false;

    private QueueAgent<DataMessage> dataMessageProcessor;

    public BaseTTSExtension() {
        super();
    }

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        super.onConfigure(env, properties);
        log.info("TTS扩展配置阶段: extensionName={}", env.getExtensionName());

        this.dataMessageProcessor = QueueAgent.create();
        this.dataMessageProcessor.subscribe(createDataMessageConsumer());
    }

    @Override
    public void onInit(TenEnv env) {
        super.onInit(env);
        log.info("TTS扩展初始化阶段: extensionName={}", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("TTS扩展启动阶段: extensionName={}", env.getExtensionName());
        isRunning = true;
        interrupted.set(false);
        dataMessageProcessor.start();
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
        log.info("TTS扩展停止阶段: extensionName={}", env.getExtensionName());
        isRunning = false;
        interrupted.set(true);
        if (dataMessageProcessor != null) {
            dataMessageProcessor.shutdown();
        }
    }

    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
        log.info("TTS扩展清理阶段: extensionName={}", env.getExtensionName());
    }

    public TenEnv getEnv() {
        return env;
    }

    /**
     * 创建用于处理 DataMessage 的消费者逻辑。
     * 这是一个内部方法，封装了 dataMessageProcessor.subscribe() 中的重复逻辑。
     */
    private Consumer<DataMessage> createDataMessageConsumer() {
        return data -> {
            try {
                if (getEnv() != null) {
                    BaseTTSExtension.this.onRequestTTS(getEnv(), data);
                } else {
                    log.error("TTS数据处理队列任务异常: TenEnv is null in consumer. DataId: {}", data.getId());
                }
            } catch (Exception e) {
                if (e.getCause() instanceof InterruptedException) {
                    log.info("onRequestTTS task was interrupted: extensionName={}", getExtensionName());
                } else {
                    log.error("TTS数据处理队列任务异常: extensionName={}, dataId={}", getExtensionName(), data.getId(),
                        e);
                }
            }
        };
    }

    // --- 抽象方法，子类必须实现 ---

    /**
     * 当收到新的TTS请求时调用。
     * 子类应实现此方法以执行实际的TTS逻辑。
     *
     * @param env  TenEnv 实例
     * @param data 包含文本和相关TTS选项的DataMessage
     */
    protected abstract void onRequestTTS(TenEnv env, DataMessage data);

    /**
     * 当TTS请求被取消时调用。
     * 子类应实现此方法以执行清理或停止TTS生成。
     *
     * @param env TenEnv 实例
     */
    protected abstract void onCancelTTS(TenEnv env);

    // --- 辅助方法 ---

    protected void sendAudioOutput(TenEnv env, byte[] audioData, int sampleRate, int bytesPerSample,
        int numberOfChannels) {
        try {
            AudioFrameMessage audioFrame = AudioFrameMessage.create("audio_frame");
            audioFrame.setId(MessageUtils.generateUniqueId()); // 设置ID
            audioFrame.setSampleRate(sampleRate);
            audioFrame.setBytesPerSample(bytesPerSample);
            audioFrame.setNumberOfChannel(numberOfChannels); // 注意这里是 numberOfChannel
            audioFrame.setSamplesPerChannel(audioData.length / (bytesPerSample * numberOfChannels));
            audioFrame.setBuf(audioData); // 使用 setBuf
            audioFrame.setType(MessageType.AUDIO_FRAME); // 设置消息类型

            env.sendMessage(audioFrame);
            log.debug("TTS音频帧发送成功: extensionName={}, size={}", env.getExtensionName(), audioData.length);
        } catch (Exception e) {
            log.error("TTS音频帧发送异常: extensionName={}", env.getExtensionName(), e);
        }
    }

    protected void sendTranscriptOutput(TenEnv env, String transcriptText, boolean isQuiet, String objectType) {
        try {
            DataMessage transcriptData = DataMessage.create(ExtensionConstants.DATA_TRANSCRIPT_NAME);
            // 根据 Python 的 AssistantTranscription 结构，构建 JSON 属性
            Map<String, Object> payload = new HashMap<>();
            payload.put("object", objectType); // "assistant.transcription"
            payload.put("text", transcriptText);
            payload.put("source", "tts");
            payload.put("quiet", isQuiet);
            // ... 其他字段如 words, start_ms, duration_ms 如果需要，也在这里添加

            transcriptData.setProperty("payload", payload); // 或根据实际需要扁平化
            // Python 的 set_property_from_json("", t.model_dump_json())
            // 在这里直接使用 Map 作为 property，或者转换为 JSON 字符串

            env.sendMessage(transcriptData);
            log.debug("TTS转录输出发送成功: extensionName={}, text={}", env.getExtensionName(), transcriptText);
        } catch (Exception e) {
            log.error("TTS转录输出发送异常: extensionName={}", env.getExtensionName(), e);
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

    public void flushInputItems(TenEnv env) {
        dataMessageProcessor.shutdown();
        this.dataMessageProcessor = QueueAgent.create();
        this.dataMessageProcessor.subscribe(createDataMessageConsumer());
        dataMessageProcessor.start();

        interrupted.set(false);
        log.info("Flushed TTS input items and reset processor: extensionName={}", env.getExtensionName());
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage data) {
        super.onDataMessage(env, data);
        if (!isRunning) {
            log.warn("TTS扩展未运行，忽略数据: extensionName={}, dataId={}",
                env.getExtensionName(), data.getId());
            return;
        }

        if (ExtensionConstants.DATA_TRANSCRIPT_NAME.equals(data.getName())) {
            dataMessageProcessor.offer(data);
            log.debug("TTS数据已加入队列: extensionName={}, dataId={}", env.getExtensionName(), data.getId());
        } else {
            log.warn("TTS扩展收到未知数据类型，忽略: extensionName={}, dataName={}",
                env.getExtensionName(), data.getName());
        }
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        super.onCmd(env, command);
        if (!isRunning) {
            log.warn("TTS扩展未运行，忽略命令: extensionName={}, commandName={}",
                env.getExtensionName(), command.getName());
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            String commandName = command.getName();
            if (ExtensionConstants.CMD_IN_FLUSH.equals(commandName)) {
                onCancelTTS(env);
                flushInputItems(env);

                // 发送 CMD_OUT_FLUSH 命令
                Command outFlushCmd = GenericCommand
                    .create(ExtensionConstants.CMD_OUT_FLUSH, command.getId(), command.getType());
                env.sendCmd(outFlushCmd);
                log.debug("TTS命令处理完成: {}", commandName);
            } else {
                CommandResult result = CommandResult.success(command, "Unknown TTS command, ignored.");
                env.sendResult(result);
            }
            long duration = System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            log.error("TTS扩展命令处理异常: extensionName={}, commandName={}",
                env.getExtensionName(), command.getName(), e);
            sendErrorResult(env, command, "TTS命令处理异常: " + e.getMessage());
        }
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        super.onAudioFrame(env, audioFrame);
        log.warn("TTS扩展不处理音频帧: extensionName={}, frameId={}",
            env.getExtensionName(), audioFrame.getId());
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) {
        super.onVideoFrame(env, videoFrame);
        log.warn("TTS扩展不处理视频帧: extensionName={}, frameId={}",
            env.getExtensionName(), videoFrame.getId());
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) {
        super.onCmdResult(env, commandResult);
        log.warn("TTS扩展收到未处理的 CommandResult: {}. OriginalCommandId: {}", env.getExtensionName(),
            commandResult.getId(), commandResult.getOriginalCommandId());
    }
}