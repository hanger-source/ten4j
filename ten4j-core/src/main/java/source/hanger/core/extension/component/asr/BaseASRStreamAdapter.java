package source.hanger.core.extension.component.asr;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.common.DefaultSchedulers;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.state.ExtensionStateProvider;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.tenenv.TenEnv;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * ASR 流服务抽象基类。
 * 负责 ASR 原始输出的复杂解析、文本聚合，并将其转换为更高级的“逻辑块”推送到主管道。
 */
@Slf4j
public abstract class BaseASRStreamAdapter<RECOGNITION_RESULT> implements ASRStreamAdapter {

    // 新增：最大重试次数和初始退避时间
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final int INITIAL_DELAY_MS = 200;
    protected final ExtensionStateProvider extensionStateProvider;
    protected final StreamPipelineChannel<OutputBlock> streamPipelineChannel;
    private final FlowableProcessor<ByteBuffer> audioInputStreamProcessor; // 新增的音频输入处理器
    private final CompositeDisposable disposables = new CompositeDisposable();
    // 新增：重连尝试次数计数器
    private final AtomicInteger retryCount = new AtomicInteger(0);
    protected transient boolean reconnecting = false;
    /**
     * 构造函数。
     *
     * @param extensionStateProvider 扩展状态提供者。
     * @param streamPipelineChannel     流管道管理器。
     */
    public BaseASRStreamAdapter(
        ExtensionStateProvider extensionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        this.extensionStateProvider = extensionStateProvider;
        this.streamPipelineChannel = streamPipelineChannel;
        this.audioInputStreamProcessor = PublishProcessor.<ByteBuffer>create().toSerialized();
    }

    @Override
    public void startASRStream(TenEnv env) {
        log.info("[{}] ASR 流式配器启动 channelId={}", env.getExtensionName(), streamPipelineChannel.uuid());

        Flowable<PipelinePacket<OutputBlock>> flowable = getRawAsrFlowable(env, audioInputStreamProcessor)
            .observeOn(DefaultSchedulers.IO_OFFLOAD_SCHEDULER)
            .flatMap(result -> transformSingleRecognitionResult(result, env))
            .takeWhile(_ -> !extensionStateProvider.isInterrupted())
            // 引入 retryWhen 实现指数退避和重试限制
            .retryWhen(throwableFlowable -> throwableFlowable.flatMap(e -> {
                if (e.getMessage().contains("timeout")) {
                    return Flowable.error(e);
                }
                int count = retryCount.incrementAndGet();
                if (count > MAX_RETRY_ATTEMPTS) {
                    // 超过最大重试次数，终止重连
                    log.error("[{}] ASR Stream max retry attempts reached, stopping reconnection. Error: {}",
                        env.getExtensionName(), e.getMessage());
                    return Flowable.error(e); // 抛出错误以终止流
                }
                // 计算指数退避延迟
                long delay = (long) (INITIAL_DELAY_MS * Math.pow(2, count - 1));
                log.warn("[{}] ASR Stream error occurred. Retrying attempt {} in {}ms. Error: {}",
                    env.getExtensionName(), count, delay, e.getMessage());

                // 使用 timer 延迟后继续重试
                return Flowable.timer(delay, MILLISECONDS, Schedulers.io());
            }))
            .doOnError(e -> {
                if (e.getMessage().contains("timeout")) {
                    env.postTask(() -> {
                        log.info("[{}] ASR Stream timeout, reconnecting... channelId={}",
                            env.getExtensionName(), streamPipelineChannel.uuid());
                        onReconnect(env);
                    });;
                } else {
                    log.error("[{}] ASR Stream final error after all retries: {}", env.getExtensionName(), e.getMessage(), e);
                }
                // 最终失败后，可以根据业务需求做一些收尾工作，比如通知上层服务
            })
            .doOnSubscribe(subscription -> {
                // 成功订阅后重置重试计数器
                retryCount.set(0);
            })
            .doOnComplete(() -> {
                // 流正常完成时，也重置重试计数器
                retryCount.set(0);
                log.info("[{}] ASR Stream completed. channelId={}",
                    env.getExtensionName(), streamPipelineChannel.uuid());
            });

        // 由于 retryWhen 已经包含了重连逻辑，我们不再需要在 doOnError/doOnComplete 中手动调用 onReconnect
        streamPipelineChannel.submitStreamPayload(flowable, env);
    }

    @Override
    public void onRequestAudioInput(TenEnv env, ByteBuffer rawAudioInput) {
        log.debug("[{}] Received audio frame with buffer size: {}", env.getExtensionName(), rawAudioInput.remaining());
        if (audioInputStreamProcessor != null
            && !audioInputStreamProcessor.hasComplete()
            && !audioInputStreamProcessor.hasThrowable()) {
            audioInputStreamProcessor.onNext(rawAudioInput);
        } else {
            log.warn("[{}] Audio input processor is not active, cannot send audio frame. ASR Stream not started or already stopped?", env.getExtensionName());
        }
    }

    @Override
    public void onReconnect(TenEnv env) {
        // 为了避免和内部重试机制冲突，我们可以让它在特定条件下触发。
        reconnecting = true;
        log.info("[{}] Starting external reconnection process. channelId={}", env.getExtensionName(),
            streamPipelineChannel.uuid());
        disposables.add(Flowable.timer(INITIAL_DELAY_MS, MILLISECONDS, Schedulers.io())
            .doOnComplete(() -> {
                env.postTask(() -> {
                    try {
                        streamPipelineChannel.recreatePipeline(env);
                        startASRStream(env);
                        log.info("[{}] Reconnection completed successfully. channelId={}",
                            env.getExtensionName(), streamPipelineChannel.uuid());
                    } catch (Exception e) {
                        log.error("[{}] Reconnection failed: {} channelId={}", env.getExtensionName(), e.getMessage(),
                            streamPipelineChannel.uuid(), e);
                        disposables.add( // 新增：将 Disposable 添加到 CompositeDisposable
                            Flowable.timer(1000, MILLISECONDS, Schedulers.io())
                                .subscribe(v -> onReconnect(env)));
                    } finally {
                        reconnecting = false;
                    }
                });
            })
            .subscribe(_ -> {
            }, e -> log.error("[{}] Reconnection timer error: {}", env.getExtensionName(), e.getMessage(), e))
        );
    }

    /**
     * 抽象方法：获取 ASR 供应商的原始响应流。
     * 由具体实现类提供。
     *
     * @param env 当前的 TenEnv 环境。
     * @param audioInputFlowable 音频输入流。
     * @return 包含原始 ASR 响应的 Flowable 流。
     */
    protected abstract Flowable<RECOGNITION_RESULT> getRawAsrFlowable(TenEnv env, Flowable<ByteBuffer> audioInputFlowable);

    /**
     * 抽象方法：处理单个 ASR 原始识别结果。
     * 从结果中提取文本片段，并将其转换为 Flowable<PipelinePacket<OutputBlock>>。
     *
     * @param result 原始 ASR 识别结果。
     * @param env    当前的 TenEnv 环境。
     * @return 包含 PipelinePacket 的 Flowable 流。
     */
    protected abstract Flowable<PipelinePacket<OutputBlock>> transformSingleRecognitionResult(RECOGNITION_RESULT result, TenEnv env);

}
