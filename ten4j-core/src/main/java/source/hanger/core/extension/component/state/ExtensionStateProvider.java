package source.hanger.core.extension.component.state;

import source.hanger.core.extension.component.flush.InterruptionStateProvider;

/**
 * Extension 状态提供者接口。
 * 统一管理 Extension 的运行状态、中断状态等所有状态信息。
 * 继承 InterruptionStateProvider 以保持向后兼容性。
 */
public interface ExtensionStateProvider extends InterruptionStateProvider {

    /**
     * 检查 Extension 是否正在运行。
     *
     * @return 如果正在运行则返回 true，否则返回 false。
     */
    boolean isRunning();

    /**
     * 设置 Extension 运行状态。
     *
     * @param running true 表示运行中，false 表示已停止。
     */
    void setRunning(boolean running);

    /**
     * 检查 Extension 是否已停止。
     *
     * @return 如果已停止则返回 true，否则返回 false。
     */
    default boolean isStopped() {
        return !isRunning();
    }

    /**
     * 检查 Extension 是否处于活跃状态（运行中且未中断）。
     *
     * @return 如果处于活跃状态则返回 true，否则返回 false。
     */
    default boolean isActive() {
        return isRunning() && !isInterrupted();
    }

    /**
     * 启动 Extension。
     * 设置运行状态为 true，并清除中断标志。
     */
    default void start() {
        setRunning(true);
        setInterrupted(false);
    }

    /**
     * 停止 Extension。
     * 设置运行状态为 false，并设置中断标志。
     */
    default void stop() {
        setInterrupted(true);
        setRunning(false);
    }

    /**
     * 重置所有状态。
     * 清除运行状态和中断标志。
     */
    default void reset() {
        setRunning(false);
        setInterrupted(false);
    }
}
