package source.hanger.core.extension.component.flush;

import java.util.function.Consumer;

import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.tenenv.TenEnv;

/**
 * 刷新操作协调器接口的实现类。
 * 负责编排完整的 flush 操作流程，包括中断信号的设置、流管道的重置以及对外发送完成通知。
 */
public class DefaultFlushOperationCoordinator implements FlushOperationCoordinator {

    private final InterruptionStateProvider interruptionStateProvider;
    private final StreamPipelineChannel<OutputBlock> streamPipelineChannel;
    private final Consumer<TenEnv> onCancelFlushCallback; // 回调 BaseFlushExtension#onCancelFlush

    /**
     * 构造函数。
     *
     * @param interruptionStateProvider 中断状态提供者。
     * @param streamPipelineChannel     流管道管理器。
     * @param onCancelFlushCallback     用于通知 BaseFlushExtension 执行取消操作的回调函数。参数为 (TenEnv env)。
     */
    public DefaultFlushOperationCoordinator(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel,
        Consumer<TenEnv> onCancelFlushCallback) {
        this.interruptionStateProvider = interruptionStateProvider;
        this.streamPipelineChannel = streamPipelineChannel;
        this.onCancelFlushCallback = onCancelFlushCallback;
    }

    @Override
    public void triggerFlush(TenEnv env) {
        // 1. 通知 BaseFlushExtension 子类进行特定清理
        if (onCancelFlushCallback != null) {
            onCancelFlushCallback.accept(env); // 动态传递 TenEnv
        }

        // 2. 设置中断标志，阻止当前流继续
        interruptionStateProvider.setInterrupted(true);

        // 3. 取消旧的管道订阅
        // 4. 重新创建 streamProcessor 并重新订阅
        streamPipelineChannel.recreatePipeline(env); // 动态传递 TenEnv

        // 5. 重置中断标志，允许新流开始
        interruptionStateProvider.setInterrupted(false);
    }
}
