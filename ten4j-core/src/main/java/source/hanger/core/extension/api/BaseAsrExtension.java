package source.hanger.core.extension.api;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.asr.ASROutputBlock;
import source.hanger.core.extension.component.asr.ASRStreamAdapter;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.flush.FlushOperationCoordinator;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.impl.DefaultFlushOperationCoordinator;
import source.hanger.core.extension.component.impl.DefaultInterruptionStateProvider;
import source.hanger.core.extension.component.impl.DefaultStreamPipelineChannel;
import source.hanger.core.extension.component.stream.StreamOutputBlockConsumer;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.MessageType;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;

@Slf4j
public abstract class BaseAsrExtension extends BaseExtension {

    private final CompositeDisposable disposables = new CompositeDisposable(); // 管理所有 Disposable
    // 成员变量
    protected StreamPipelineChannel<ASROutputBlock> streamPipelineChannel;
    protected ASRStreamAdapter asrStreamAdapter;

    // 构造函数
    public BaseAsrExtension() {
    }

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        log.info("[{}] 配置中，初始化核心组件。", env.getExtensionName());

        // 2. 初始化 StreamPipelineChannel (通用实现)
        this.streamPipelineChannel = createStreamPipelineChannel(createASROutputBlockConsumer());
        log.info("[{}] 配置中，初始化 StreamPipelineChannel。", env.getExtensionName());

        // 3. 初始化 ASRStreamAdapter (由子类提供具体实现)
        this.asrStreamAdapter = createASRStreamAdapter();
        log.info("[{}] 配置中，初始化 ASRStreamAdapter。", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[{}] BaseAsrExtension 启动，初始化管道。", env.getExtensionName());
        streamPipelineChannel.initPipeline(env);
        asrStreamAdapter.startASRStream(env); // 调用 ASRStreamAdapter 的启动方法
    }

    @Override
    public void onDeinit(TenEnv env) {
        log.info("[{}] ASR扩展清理，停止连接.", env.getExtensionName());
        streamPipelineChannel.disposeCurrent();
        disposables.clear(); // 清理所有定时器 Disposable
        asrStreamAdapter.onDeinit(env); // 调用 ASRStreamAdapter 的清理方法
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        if (!isRunning()) {
            log.warn("[{}] ASR Extension未运行，忽略音频帧: frameId={}",
                env.getExtensionName(), audioFrame.getId());
            return;
        }
        asrStreamAdapter.onAudioFrame(env, audioFrame); // 将音频帧转发给 ASRStreamAdapter
    }

    protected synchronized void handleReconnect(TenEnv env) {
        log.info("[{}] Starting reconnection process.", env.getExtensionName());
        disposables.add(Flowable.timer(200, TimeUnit.MILLISECONDS, Schedulers.io()).doOnComplete(() -> {
                    try {
                        asrStreamAdapter.onReconnect(env); // 调用 ASRStreamAdapter 的重连方法
                        log.info("[{}] Reconnection completed successfully.", env.getExtensionName());
                    } catch (Exception e) {
                        log.error("[{}] Reconnection failed: {}", env.getExtensionName(), e.getMessage(), e);
                        disposables.add( // 新增：将 Disposable 添加到 CompositeDisposable
                            Flowable.timer(1000, TimeUnit.MILLISECONDS, Schedulers.io())
                                .subscribe(v -> handleReconnect(env)));
                    }
                })
                .subscribe() // 修正：直接调用 subscribe() 来获取 Disposable
        );
    }

    protected void sendAsrError(TenEnv env, String messageId, MessageType messageType, String messageName,
        String errorMessage) {
        String finalMessageId = (messageId != null && !messageId.isEmpty()) ? messageId
            : MessageUtils.generateUniqueId();
        CommandResult errorResult = CommandResult.fail(finalMessageId, messageType, messageName, errorMessage);
        env.sendResult(errorResult);
        log.error("[{}] ASR Error [{}]: {}", env.getExtensionName(), messageName, errorMessage);
    }

    /**
     * 抽象方法：创建 InterruptionStateProvider 实例。
     * 子类可以返回 DefaultInterruptionStateProvider 的实例。
     */
    protected InterruptionStateProvider createInterruptionStateProvider() {
        return new DefaultInterruptionStateProvider();
    }

    /**
     * 抽象方法：创建 StreamPipelineChannel 实例。
     */
    protected StreamPipelineChannel<ASROutputBlock> createStreamPipelineChannel(
        StreamOutputBlockConsumer streamOutputBlockConsumer) {
        return new DefaultStreamPipelineChannel(interruptionStateProvider, streamOutputBlockConsumer);
    }

    /**
     * 抽象方法：创建 ASRStreamAdapter 实例。
     * 子类应返回 ASRStreamAdapter 的具体实现，例如 ParaformerASRStreamAdapter。
     */
    protected abstract ASRStreamAdapter createASRStreamAdapter();

    /**
     * 抽象方法：创建 ASRStreamOutputBlockConsumer 实例。
     */
    protected abstract StreamOutputBlockConsumer createASROutputBlockConsumer();

    /**
     * 抽象方法：创建 FlushOperationCoordinator 实例。
     * 子类应返回 FlushOperationCoordinator 的具体实现，例如 DefaultFlushOperationCoordinator。
     *
     * @param interruptionStateProvider 中断状态提供者。
     * @param streamPipelineChannel     流管道管理器。
     * @param onCancelFlushCallback     用于通知 ASRStreamAdapter 执行取消操作的回调函数。
     */
    protected FlushOperationCoordinator createFlushOperationCoordinator(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel,
        java.util.function.Consumer<TenEnv> onCancelFlushCallback) {
        return new DefaultFlushOperationCoordinator(interruptionStateProvider,
            streamPipelineChannel, onCancelFlushCallback);
    }
}
