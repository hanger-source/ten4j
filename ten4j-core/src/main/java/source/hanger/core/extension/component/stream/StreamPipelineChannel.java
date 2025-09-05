package source.hanger.core.extension.component.stream;

import io.reactivex.Flowable;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.tenenv.TenEnv;

/**
 * 流管道管理器接口。
 * 负责拥有、创建和管理 Extension 的主数据流管道。
 */
public interface StreamPipelineChannel<T extends OutputBlock> { // 引入泛型 T，约束为 LLMOutputBlock 的子类

    String uuid();
    /**
     * 初始化流管道，并订阅其数据流。
     * 此方法应在 Extension 启动时调用。
     *
     * @param env 当前的 TenEnv 环境。
     */
    void initPipeline(TenEnv env);

    /**
     * 重新创建流管道。
     * 通常在刷新（flush）操作中调用，以清理旧流并为新流做好准备。
     *
     * @param env 当前的 TenEnv 环境。
     */
    void recreatePipeline(TenEnv env);

    /**
     * 取消当前流的订阅。
     * 停止数据流动，但不重新创建管道。
     */
    void disposeCurrent();

    /**
     * 提交新的流数据负载到管道。
     *
     * @param flowable 包含 PipelinePacket<T> 的 Flowable 流。
     * @param env      env
     */
    void submitStreamPayload(Flowable<PipelinePacket<T>> flowable, TenEnv env);
}
