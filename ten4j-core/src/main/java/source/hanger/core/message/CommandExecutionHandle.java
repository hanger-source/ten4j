package source.hanger.core.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

import lombok.Getter;
import source.hanger.core.runloop.Runloop;

/**
 * {@code CommandExecutionHandle} 封装了异步命令的执行过程和其结果流。
 * 它提供了一种灵活的方式来处理命令执行期间可能产生的多个中间结果、
 * 阶段性最终结果，以及命令生命周期的最终完成状态。
 * 同时，它也兼容了传统的 Future 模型，方便获取所有完成结果的列表。
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li><b>流式结果订阅:</b> 通过 {@code toPublisher()} 方法，
 *     允许消费者订阅命令执行过程中产生的所有 {@link CommandResult} 流。
 *     这适用于需要实时进度更新或处理多个阶段性结果的场景。</li>
 *   <li><b>一次性完成 Future:</b> 通过 {@code toCompletedFuture()} 方法，
 *     返回一个 {@link CompletableFuture<List<CommandResult>>}。这个 Future 将在命令
 *     整个生命周期结束后（即底层流发出 {@code onComplete} 信号时），
 *     使用收集到的所有 {@link CommandResult} 列表来完成。如果命令在完成前出现错误，
 *     Future 将以异常结束。</li>
 * </ul>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>对于需要监控命令执行进度、处理多阶段结果或构建响应式管道的场景，
 *       可以直接订阅 {@code CommandResult} 流。</li>
 *   <li>对于只需要命令最终所有结果列表的简单场景，可以使用 {@code toCompletedFuture()}。</li>
 * </ul>
 *
 * @param <T> 命令执行过程中产生的具体结果类型，通常是 {@link CommandResult}。
 */
public class CommandExecutionHandle<T> {

    private final SubmissionPublisher<T> publisher; // 内部的 Publisher
    private final CompletableFuture<List<T>> future; // 聚合所有结果的 Future
    private final List<T> results; // 收集中间结果

    /**
     * 构造函数。
     *
     * @param runloop 用于调度 Publisher 事件的 Runloop。
     */
    public CommandExecutionHandle(Runloop runloop) {
        this.publisher = new SubmissionPublisher<>(runloop::postTask, 256); // 使用硬编码的默认缓冲区大小
        this.future = new CompletableFuture<>();
        this.results = Collections.synchronizedList(new ArrayList<>());

        // 订阅 publisher，当所有 T 都发布完毕后，完成 future
        publisher.subscribe(new Flow.Subscriber<T>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE); // 请求所有数据
            }

            @Override
            public void onNext(T item) {
                results.add(item); // 收集所有结果
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable); // 发生错误，Future 以异常结束
                if (subscription != null) {
                    subscription.cancel(); // 取消订阅
                }
            }

            @Override
            public void onComplete() {
                future.complete(results); // 使用收集到的所有结果完成 Future
            }
        });
    }

    /**
     * 将命令的执行结果作为响应式流暴露。
     * 允许消费者订阅并接收命令执行过程中产生的所有结果。
     *
     * @return 一个 {@link Flow.Publisher}，发布结果。
     */
    public Flow.Publisher<T> toPublisher() {
        return publisher;
    }

    /**
     * 将命令的最终完成结果以 {@link CompletableFuture} 的形式返回。
     * 这个 Future 将会等待直到命令的整个生命周期结束（即底层流 {@code onComplete}），
     * 并使用收集到的所有结果列表来完成此 Future。
     * 如果在完成前遇到错误，Future 将以异常结束。
     *
     * @return 一个 {@link CompletableFuture}，代表命令的最终完成结果列表。
     */
    public CompletableFuture<List<T>> toCompletedFuture() {
        return future;
    }

    /**
     * 向内部 Publisher 提交一个结果。
     *
     * @param item 要提交的结果。
     */
    public void submit(T item) {
        publisher.submit(item);
    }

    /**
     * 正常关闭内部 Publisher。
     */
    public void close() {
        publisher.close();
    }

    /**
     * 异常关闭内部 Publisher。
     *
     * @param error 导致关闭的异常。
     */
    public void closeExceptionally(Throwable error) {
        publisher.closeExceptionally(error);
    }

    /**
     * 将此 CommandExecutionHandle 的事件调度迁移到指定的 Runloop。
     * 创建并返回一个新的 CommandExecutionHandle，其所有事件（onNext, onComplete, onError）
     * 都将在 targetRunloop 上调度和执行。原始 CommandExecutionHandle 的事件流
     * 会被管道到这个新的 handle 中。
     *
     * @param targetRunloop 目标 Runloop，新的 CommandExecutionHandle 将绑定到此 Runloop。
     * @return 一个新的 CommandExecutionHandle 实例，其回调将在 targetRunloop 上执行。
     */
    public CommandExecutionHandle<T> onRunloop(Runloop targetRunloop) {
        // 创建一个 RunloopShiftingProcessor，它会确保其下游的 onNext/onComplete/onError
        // 都在 targetRunloop 上调度。
        RunloopShiftingProcessor<T> processor = new RunloopShiftingProcessor<>(targetRunloop);

        // 订阅当前 handle 的 Publisher 到这个 Processor。这意味着 processor 的 onNext/onError/onComplete
        // 会在当前 handle 的 Runloop 上被调用（这里是 Engine 的 Runloop）。
        // 但 processor 会将这些事件提交到其内部的 Publisher，而这个内部 Publisher
        // 会在 targetRunloop 上重新调度这些事件。
        this.toPublisher().subscribe(processor);

        // 返回一个包装了 RunloopShiftingProcessor 的 CommandExecutionHandle。
        // 这个新的 CommandExecutionHandle 将使用 processor 作为其主要的 Publisher
        // 这样任何对新 handle 的 toPublisher() 和 toCompletedFuture() 的调用，
        // 都将确保回调在 targetRunloop 上执行。
        return new CommandExecutionHandle<T>(targetRunloop) {
            // 覆盖 toPublisher 方法，使其返回 RunloopShiftingProcessor
            @Override
            public Flow.Publisher<T> toPublisher() {
                return processor;
            }

            // 覆盖 toCompletedFuture 方法，确保它的 CompletableFuture 也是由 processor 驱动完成的
            // 这里的逻辑需要和 RunloopShiftingProcessor 内部的 Future 完成逻辑联动
            // 为了简化，RunloopShiftingProcessor 内部也维护一个 CompletableFuture
            @Override
            public CompletableFuture<List<T>> toCompletedFuture() {
                return processor.getCompletedFuture();
            }
        };
    }

    /**
     * 内部类，用于在不同的 Runloop 之间迁移 Flow 事件调度。
     * 它作为一个 Flow.Processor，订阅上游 Publisher 的事件，然后通过其内部
     * 绑定到特定 Runloop 的 SubmissionPublisher 重新发布这些事件。
     */
    private static class RunloopShiftingProcessor<T> implements Flow.Processor<T, T> {
        private final SubmissionPublisher<T> publisher; // 用于在目标 Runloop 上发布事件
        @Getter
        private final CompletableFuture<List<T>> completedFuture; // 聚合所有结果的 Future
        private final List<T> results; // 收集中间结果
        private Flow.Subscription upstreamSubscription; // 对上游订阅的引用

        public RunloopShiftingProcessor(Runloop targetRunloop) {
            this.publisher = new SubmissionPublisher<>(targetRunloop::postTask, 256);
            this.completedFuture = new CompletableFuture<>();
            this.results = Collections.synchronizedList(new ArrayList<>());

            // 订阅 publisher，当所有 T 都发布完毕后，完成 completedFuture
            publisher.subscribe(new Flow.Subscriber<T>() {
                private Flow.Subscription s;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.s = subscription;
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(T item) {
                    results.add(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    completedFuture.completeExceptionally(throwable);
                    if (s != null) {
                        s.cancel();
                    }
                }

                @Override
                public void onComplete() {
                    completedFuture.complete(results);
                }
            });
        }

        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            publisher.subscribe(subscriber); // 将下游订阅者连接到内部的 Publisher
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.upstreamSubscription = subscription; // 存储上游订阅，以便请求更多数据或取消
            subscription.request(Long.MAX_VALUE); // 请求所有数据
        }

        @Override
        public void onNext(T item) {
            // 这个方法由上游 Publisher 调用，可能在不同的线程上。
            // 但我们将 item 提交到我们自己的 Publisher，它会确保在 targetRunloop 上调度。
            publisher.submit(item);
        }

        @Override
        public void onError(Throwable throwable) {
            publisher.closeExceptionally(throwable); // 异常关闭我们的 Publisher
        }

        @Override
        public void onComplete() {
            publisher.close(); // 正常关闭我们的 Publisher
        }
    }
}
