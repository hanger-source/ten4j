package source.hanger.core.extension.component.flush;

import source.hanger.core.tenenv.TenEnv;

/**
 * 刷新操作协调器接口。
 * 负责编排完整的 flush 操作流程，包括中断信号的设置、流管道的重置以及对外发送完成通知。
 */
public interface FlushOperationCoordinator {

    /**
     * 触发完整的刷新（flush）操作。
     * 此方法是执行刷新流程的唯一入口，它将协调内部组件来完成中断、管道重置和消息发送等步骤。
     *
     * @param env 当前的 TenEnv 环境。
     */
    void triggerFlush(TenEnv env);
}
