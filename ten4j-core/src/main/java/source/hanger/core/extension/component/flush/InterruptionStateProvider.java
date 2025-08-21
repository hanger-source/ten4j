package source.hanger.core.extension.component.flush;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 中断状态提供者接口。
 * 负责拥有、设置和查询全局中断标志。
 */
public interface InterruptionStateProvider {

    /**
     * 检查是否已中断。
     *
     * @return 如果已中断则返回true，否则返回false。
     */
    boolean isInterrupted();

    /**
     * 设置中断状态。
     *
     * @param value true 表示中断，false 表示解除中断。
     */
    void setInterrupted(boolean value);

    /**
     * 获取用于中断检测的 AtomicBoolean 标志。
     * 允许其他组件（如 RxJava 的 takeWhile）直接访问和检查此共享标志。
     *
     * @return 用于指示中断状态的 AtomicBoolean 对象。
     */
    AtomicBoolean getInterruptedFlag();
}
