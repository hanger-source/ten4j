package source.hanger.core.extension.system.tts;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.UnicastProcessor;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.BaseExtension;
import source.hanger.core.extension.system.ExtensionConstants;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;

/**
 * TTS基础抽象类
 * 基于ten-framework AI_BASE的tts.py设计
 *
 * 核心特性：
 * 1. 异步处理队列机制 (通过 UnicastProcessor 串联所有音频流)
 * 2. 音频数据流式输出
 * 3. 输入数据处理与取消支持
 */
@Slf4j
public abstract class BaseTTSExtension extends BaseExtension {

    protected final AtomicBoolean interrupted = new AtomicBoolean(false);
    protected boolean isRunning = false;
    // 统一的流发布器，所有音频流通过它排队并依次执行，保证顺序，toSerialized保证线程安全
    private FlowableProcessor<Flowable<byte[]>> streamProcessor = UnicastProcessor.<Flowable<byte[]>>create()
        .toSerialized();
    // 维护所有活跃流的 Disposable，支持取消
    private Disposable disposable;

    public BaseTTSExtension() {
        super();
    }

    /**
     * 启动时调用
     */
    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("TTS扩展启动: extensionName={}", env.getExtensionName());
        isRunning = true;
        interrupted.set(false);
        disposable = generateDisposable(env);
    }

    private Disposable generateDisposable(TenEnv env) {
        return streamProcessor
            .onBackpressureBuffer()
            .concatMap(flowable -> flowable
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                // 中断检测，flush后中断当前流处理
                .takeWhile(bytes -> !interrupted.get())
            )
            .subscribe(
                audioBytes -> handleAudioChunk(audioBytes, env),
                error -> log.error("TTS 流异常", error),
                () -> log.info("TTS 流处理完成")
            );
    }

    /**
     * 停止时调用，取消所有流
     */
    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
        log.info("TTS扩展停止: extensionName={}", env.getExtensionName());
        isRunning = false;
        interrupted.set(true);
        disposeCurrent();
    }

    /**
     * 清理时调用
     */
    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
        log.info("TTS扩展清理: extensionName={}", env.getExtensionName());
        disposeCurrent();
    }

    /**
     * 新数据消息到来，调用抽象的 onRequestTTS 生成音频流，推送到 processor
     */
    @Override
    public void onDataMessage(TenEnv env, DataMessage data) {
        if (!isRunning) {
            log.warn("TTS扩展未运行，忽略数据: extensionName={}, dataId={}", env.getExtensionName(), data.getId());
            return;
        }

        Flowable<byte[]> audioFlow = onRequestTTS(env, data);
        if (audioFlow != null) {
            log.debug("推送新音频流到streamProcessor, extensionName={}, dataId={}", env.getExtensionName(),
                data.getId());
            streamProcessor.onNext(audioFlow);
        }
    }

    /**
     * 发送音频数据块，子类可重写实现具体发送逻辑
     */
    protected void handleAudioChunk(byte[] audioData, TenEnv env) {
        // 默认示例实现，具体发送音频帧
        sendAudioOutput(env, audioData, 24000, 2, 1);
    }

    /**
     * 抽象方法：处理TTS请求，返回音频数据流
     * 子类必须实现
     */
    protected abstract Flowable<byte[]> onRequestTTS(TenEnv env, DataMessage data);

    /**
     * 取消TTS，子类覆盖实现清理逻辑
     */
    protected abstract void onCancelTTS(TenEnv env);

    /**
     * 刷新输入，取消所有待处理和活跃流
     * 重新创建流发布器和订阅，保证新流可以正常消费
     */
    public synchronized void flushInputItems(TenEnv env, Command command) {
        log.info("刷新TTS输入并取消活跃流: extensionName={}", env.getExtensionName());
        interrupted.set(true); // 先设置中断，切断当前流的处理
        disposeCurrent();      // 取消订阅，释放资源
        // 重新创建流发布器（线程安全的）
        streamProcessor = UnicastProcessor.<Flowable<byte[]>>create().toSerialized();
        interrupted.set(false); // 解除中断，准备处理新流
        disposable = generateDisposable(env); // 重新订阅新流
    }

    private synchronized void disposeCurrent() {
        if (disposable != null && !disposable.isDisposed()) {
            try {
                disposable.dispose();
                log.debug("已取消当前流订阅");
            } catch (Exception e) {
                log.warn("取消流订阅异常", e);
            }
        }
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        if (!isRunning) {
            log.warn("TTS扩展未运行，忽略命令: extensionName={}, commandName={}", env.getExtensionName(),
                command.getName());
            return;
        }
        try {
            String commandName = command.getName();
            if (ExtensionConstants.CMD_IN_FLUSH.equals(commandName)) {
                onCancelTTS(env);
                flushInputItems(env, command);
                Command outFlushCmd = GenericCommand.create(ExtensionConstants.CMD_OUT_FLUSH, command.getId(),
                    command.getType());
                //env.sendCmd(outFlushCmd);
                log.info("TTS命令处理完成: {}", commandName);
            } else {
                CommandResult result = CommandResult.success(command, "未知TTS命令，忽略");
                env.sendResult(result);
            }
        } catch (Exception e) {
            log.error("TTS扩展命令处理异常: extensionName={}, commandName={}", env.getExtensionName(),
                command.getName(), e);
            sendErrorResult(env, command.getId(), command.getType(), "",
                "TTS命令处理异常: %s".formatted(e.getMessage()));
        }
    }

    // 发送音频帧
    protected void sendAudioOutput(TenEnv env, byte[] audioData, int sampleRate, int bytesPerSample,
        int numberOfChannels) {
        try {
            AudioFrameMessage audioFrame = AudioFrameMessage.create("audio_frame");
            audioFrame.setId(MessageUtils.generateUniqueId());
            audioFrame.setSampleRate(sampleRate);
            audioFrame.setBytesPerSample(bytesPerSample);
            audioFrame.setNumberOfChannel(numberOfChannels);
            audioFrame.setSamplesPerChannel(audioData.length / (bytesPerSample * numberOfChannels));
            audioFrame.setBuf(audioData);
            audioFrame.setType(MessageType.AUDIO_FRAME);
            env.sendMessage(audioFrame);
            log.debug("发送音频帧成功: extensionName={}, size={}", env.getExtensionName(), audioData.length);
        } catch (Exception e) {
            log.error("发送音频帧异常: extensionName={}", env.getExtensionName(), e);
        }
    }

    // 发送错误结果
    protected void sendErrorResult(TenEnv env, String messageId, MessageType messageType, String messageName,
        String errorMessage) {
        String finalMessageId = (messageId != null && !messageId.isEmpty()) ? messageId
            : MessageUtils.generateUniqueId();
        CommandResult errorResult = CommandResult.fail(finalMessageId, messageType, messageName, errorMessage);
        env.sendResult(errorResult);
    }
}
