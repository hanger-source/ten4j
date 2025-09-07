package source.hanger.core.message;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.reactivex.Flowable;
import source.hanger.core.runloop.Runloop;

/**
 * {@code CommandExecutionHandle} 接口定义了异步命令执行过程和其结果流的公共契约。
 * 它提供了一种灵活的方式来处理命令执行期间可能产生的多个中间结果、
 * 阶段性最终结果，以及命令生命周期的最终完成状态。
 * 同时，它也兼容了传统的 Future 模型，方便获取所有完成结果的列表。
 *
 * @param <T> 命令执行过程中产生的具体结果类型，通常是 {@link CommandResult}。
 */
public interface CommandExecutionHandle<T> {

    /**
     * 将命令的执行结果作为响应式流暴露。
     * 允许消费者订阅并接收命令执行过程中产生的所有结果。
     *
     * @return 一个 {@link Flowable}，发布结果。
     */
    Flowable<T> toFlowable();

    /**
     * 将命令的最终完成结果以 {@link CompletableFuture} 的形式返回。
     * 这个 Future 将会等待直到命令的整个生命周期结束（即底层流发出 {@code onComplete} 信号时），
     * 并使用收集到的所有 {@link CommandResult} 列表来完成。如果命令在完成前出现错误，
     * Future 将以异常结束。
     *
     * @return 一个 {@link CompletableFuture}，代表命令的最终完成结果列表。
     */
    CompletableFuture<List<T>> toCompletedFuture();

    /**
     * 向内部发布器提交一个结果。
     *
     * @param item 要提交的结果。
     */
    void submit(T item);

    /**
     * 正常关闭内部发布器。
     */
    void close();

    /**
     * 异常关闭内部发布器。
     *
     * @param error 导致关闭的异常。
     */
    void closeExceptionally(Throwable error);

    /**
     * 将此 CommandExecutionHandle 的事件调度迁移到指定的 Runloop。
     * 创建并返回一个新的 CommandExecutionHandle，其所有事件（onNext, onComplete, onError）
     * 都将在 targetRunloop 上调度和执行。
     *
     * @param targetRunloop 目标 Runloop，新的 CommandExecutionHandle 将绑定到此 Runloop。
     * @return 一个新的 CommandExecutionHandle 实例，其回调将在 targetRunloop 上执行。
     */
    CommandExecutionHandle<T> onRunloop(Runloop targetRunloop);
}
