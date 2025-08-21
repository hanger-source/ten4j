package source.hanger.core.extension.component.stream;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.tenenv.TenEnv;

/**
 * 流管道管理器接口的实现类。
 * 负责拥有、创建和管理 Extension 的主数据流管道。
 */
@Slf4j
public class DefaultStreamPipelineChannel implements StreamPipelineChannel<OutputBlock> { // 实现泛型接口

    private final InterruptionStateProvider interruptionStateProvider;
    private final StreamOutputBlockConsumer<OutputBlock> streamOutputBlockConsumer; // 类型改为新的 StreamItemHandler 接口
    private FlowableProcessor<PipelinePacket<OutputBlock>> streamProcessor;
    private Disposable disposable;

    /**
     * 构造函数。
     *
     * @param interruptionStateProvider 中断状态提供者。
     * @param streamOutputBlockConsumer 用于处理流中每个数据项的回调函数。参数为 (LLMOutputBlock item, Message originalMessage, TenEnv
     *                                  env)。
     */
    public DefaultStreamPipelineChannel(
        InterruptionStateProvider interruptionStateProvider,
        StreamOutputBlockConsumer<OutputBlock> streamOutputBlockConsumer) { // 构造函数参数类型改为 StreamItemHandler
        this.interruptionStateProvider = interruptionStateProvider;
        this.streamOutputBlockConsumer = streamOutputBlockConsumer;
    }

    @Override
    public void initPipeline(TenEnv env) {
        // 确保只初始化一次，或在重新创建之前调用 disposeCurrent()
        if (streamProcessor == null) { // 简化检查，只检查是否为空
            recreatePipeline(env);
        }
    }

    @Override
    public void recreatePipeline(TenEnv env) {
        disposeCurrent(); // 先清理旧的订阅
        // streamProcessor 处理的是 PipelinePacket<LLMOutputBlock>
        streamProcessor = PublishProcessor.<PipelinePacket<OutputBlock>>create().toSerialized();
        // 建立主订阅，实际处理逻辑在这里
        disposable = streamProcessor
            .filter(packet -> !interruptionStateProvider.isInterrupted())
            .subscribe(
                packet -> streamOutputBlockConsumer.consumeOutputBlock(packet.item(), packet.originalMessage(), env),
                // 动态传递 TenEnv
                error -> log.error("[{}] StreamPipelineManager: 主管道处理错误", env.getExtensionName()),
                () -> log.info("[{}] StreamPipelineManager: 主管道处理完成", env.getExtensionName())
            );
    }

    @Override
    public void disposeCurrent() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        if (streamProcessor != null) {
            streamProcessor.onComplete(); // 完成并释放资源，无论是否有订阅者
        }
    }

    @Override
    public void submitStreamPayload(Flowable<PipelinePacket<OutputBlock>> flowable, TenEnv env) {
        // 检查 streamProcessor 是否已初始化，Disposable 未 disposed 且未中断
        if (streamProcessor != null && disposable != null && !disposable.isDisposed()
            && !interruptionStateProvider.isInterrupted()) {
            // flowable 已包含 PipelinePacket，直接订阅
            flowable.subscribe(streamProcessor);
        } else {
            log.error(
                "[{}] 管道未准备好或已中断，无法提交流。如果此错误在生产环境中频繁出现，请检查组件生命周期和 flush 逻辑。",
                env.getExtensionName());
        }
    }
}
