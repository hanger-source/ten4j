package source.hanger.core.tenenv;

import source.hanger.core.runloop.Runloop;

/**
 * `Env` 是一个通用的环境接口，定义了与运行时环境交互的基本方法。
 * 所有具体的环境接口（如 TenEnv）都应继承此接口。
 */
public interface Env {

    /**
     * 将一个任务发布到当前 Env 关联的 Runloop 线程上执行。
     * 这是确保所有操作都在正确的线程上下文中执行的关键。
     *
     * @param task 要执行的任务。
     */
    void postTask(Runnable task);

    /**
     * 获取当前 Env 关联的 Runloop 实例。
     *
     * @return 关联的 Runloop 实例。
     */
    Runloop getRunloop();
}
