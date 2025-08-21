package source.hanger.core.extension.component.asr;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;

import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.common.ASROutputBlock;
import source.hanger.core.extension.component.common.ASRTextOutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.common.RecognitionResultOutputBlock;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.tenenv.TenEnv;

/**
 * ASR 流服务抽象基类。
 * 负责 ASR 原始输出的复杂解析、文本聚合，并将其转换为更高级的“逻辑块”推送到主管道。
 */
@Slf4j
public abstract class BaseASRStreamAdapter<RECOGNITION_RESULT> implements ASRStreamAdapter {

    protected final InterruptionStateProvider interruptionStateProvider;
    protected final StreamPipelineChannel<ASROutputBlock> streamPipelineChannel;
    protected final AtomicBoolean stopped = new AtomicBoolean(false);
    protected final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final CompositeDisposable disposables = new CompositeDisposable();
    protected TenEnv tenEnv;
    private Disposable asrStreamDisposable;

    /**
     * 构造函数。
     *
     * @param interruptionStateProvider 中断状态提供者。
     * @param streamPipelineChannel     流管道管理器。
     */
    public BaseASRStreamAdapter(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<ASROutputBlock> streamPipelineChannel) {
        this.interruptionStateProvider = interruptionStateProvider;
        this.streamPipelineChannel = streamPipelineChannel;
    }

    @Override
    public void startASRStream(TenEnv env) {
        log.info("[{}] ASR 流适配器启动", env.getExtensionName());
        this.tenEnv = env;
        if (asrStreamDisposable != null && !asrStreamDisposable.isDisposed()) {
            asrStreamDisposable.dispose();
        }

        asrStreamDisposable = getRawAsrFlowable(env)
            .observeOn(Schedulers.computation())
            .flatMap(result -> transformSingleRecognitionResult(result, env))
            .takeWhile(_ -> !interruptionStateProvider.isInterrupted())
            .doOnError(e -> {
                log.error("[{}] ASR Stream error: {}", env.getExtensionName(), e.getMessage(), e);
                if (!stopped.get()) {
                    handleReconnect(env);
                } else {
                    log.info("[{}] Extension stopped, not retrying on stream error.",
                        env.getExtensionName());
                }
            })
            .doOnComplete(() -> {
                log.info("[{}] ASR Stream completed.", env.getExtensionName());
                if (!stopped.get()) {
                    handleReconnect(env);
                } else {
                    log.info("[{}] Extension stopped, not reconnecting on stream completion.",
                        env.getExtensionName());
                }
            })
            .subscribe(packet -> streamPipelineChannel.submitPipelinePacket(packet));
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        log.debug("[{}] Received audio frame with buffer size: {}", env.getExtensionName(), audioFrame.getBuf().length);
        // 交给具体实现类处理发送音频帧
        sendAudioFrameToAsrClient(env, audioFrame);
    }

    @Override
    public void onCancelASR(TenEnv env) {
        log.info("[{}] 收到取消ASR请求", env.getExtensionName());
        if (asrStreamDisposable != null && !asrStreamDisposable.isDisposed()) {
            asrStreamDisposable.dispose();
        }
    }

    @Override
    public void onDeinit(TenEnv env) {
        disposeAsrStreamsInternal();
        log.info("[{}] ASRStreamAdapter 清理.", env.getExtensionName());
        onClientStop();
    }

    @Override
    public void onReconnect(TenEnv env) {
        if (reconnecting.get()) {
            log.debug("[{}] Reconnection already in progress, skipping.", env.getExtensionName());
            return;
        }

        reconnecting.set(true);
        log.info("[{}] Starting reconnection process.", env.getExtensionName());

        disposables.add(Flowable.timer(200, TimeUnit.MILLISECONDS, Schedulers.io()).doOnComplete(() -> {
                    try {
                        disposeAsrStreamsInternal();
                        Thread.sleep(200);
                        onClientInit(); // 调用抽象方法进行客户端初始化
                        startASRStream(env);
                        log.info("[{}] Reconnection completed successfully.", env.getExtensionName());
                    } catch (Exception e) {
                        log.error("[{}] Reconnection failed: {}", env.getExtensionName(), e.getMessage(), e);
                        disposables.add(
                            Flowable.timer(1000, TimeUnit.MILLISECONDS, Schedulers.io())
                                .subscribe(v -> onReconnect(env)));
                    } finally {
                        reconnecting.set(false);
                    }
                })
                .subscribe()
        );
    }

    protected void disposeAsrStreamsInternal() {
        if (asrStreamDisposable != null && !asrStreamDisposable.isDisposed()) {
            asrStreamDisposable.dispose();
        }
        disposables.clear();
    }

    /**
     * 抽象方法：获取 ASR 供应商的原始响应流。
     * 由具体实现类提供。
     *
     * @param env 当前的 TenEnv 环境。
     * @return 包含原始 ASR 响应的 Flowable 流。
     */
    protected abstract Flowable<RECOGNITION_RESULT> getRawAsrFlowable(TenEnv env);

    /**
     * 抽象方法：发送音频帧到 ASR 客户端。
     * 由具体实现类提供。
     *
     * @param env        当前的 TenEnv 环境。
     * @param audioFrame 音频帧消息。
     */
    protected abstract void sendAudioFrameToAsrClient(TenEnv env, AudioFrameMessage audioFrame);

    /**
     * 抽象方法：处理单个 ASR 原始识别结果。
     * 从结果中提取文本片段，并将其转换为 Flowable<PipelinePacket<ASROutputBlock>>。
     *
     * @param result 原始 ASR 识别结果。
     * @param env    当前的 TenEnv 环境。
     * @return 包含 PipelinePacket 的 Flowable 流。
     */
    protected Flowable<PipelinePacket<ASROutputBlock>> transformSingleRecognitionResult(
        RECOGNITION_RESULT result,
        TenEnv env
    ) {
        // 默认实现：将 RecognitionResult 包装为 RecognitionResultOutputBlock 和 ASRTextOutputBlock
        // 子类可以重写此方法以实现更复杂的转换逻辑
        // TODO: 这里需要从 RECOGNITION_RESULT 中提取 RecognitionResult 对象。
        // 目前的 RecognitionResultOutputBlock 和 ASRTextOutputBlock 是依赖于 com.alibaba.dashscope.audio.asr.recognition.RecognitionResult 的
        // 如果需要完全泛型化，需要定义一个通用的接口来表示识别结果。
        // 暂时先保留对 RecognitionResult 的引用，在实现类中进行类型转换。
        // 或者，可以将 RecognitionResult 的提取也抽象为 protected abstract 方法。
        if (result instanceof RecognitionResult dashScopeRecognitionResult) {
            // 从 env 获取 originalMessageId
            String originalMessageId = env.getPropertyString("original_message_id").orElse(null);
            return Flowable.just(
                new PipelinePacket<>(new RecognitionResultOutputBlock(originalMessageId, dashScopeRecognitionResult), null), // originalMessageId is null here, will be set by BaseAsrExtension
                new PipelinePacket<>(new ASRTextOutputBlock(originalMessageId, dashScopeRecognitionResult.getSentence().getText(), dashScopeRecognitionResult.isSentenceEnd(),
                    dashScopeRecognitionResult.getSentence().getBeginTime(),
                    dashScopeRecognitionResult.getSentence().getEndTime() != null && dashScopeRecognitionResult.getSentence().getBeginTime() != null
                        ? dashScopeRecognitionResult.getSentence().getEndTime() - dashScopeRecognitionResult.getSentence().getBeginTime()
                        : 0L), null) // originalMessageId is null here, will be set by BaseAsrExtension
            );
        } else {
            return Flowable.empty(); // 或者抛出异常，取决于需求
        }
    }

    /**
     * 抽象方法：停止 ASR 客户端。
     * 由具体实现类提供。
     */
    protected abstract void onClientStop();

    /**
     * 抽象方法：初始化 ASR 客户端。
     * 由具体实现类提供。
     */
    protected abstract void onClientInit(); // 移除 TenEnv env 参数
}
