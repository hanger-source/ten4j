package source.hanger.core.message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.reactivex.Flowable;
import io.reactivex.FlowableSubscriber;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscription;
import source.hanger.core.runloop.Runloop;

/**
 * {@code CommandExecutionHandle} 接口的默认实现，使用 RxJava 的 {@link Flowable} 和
 * {@link CompletableFuture}。
 */
@Slf4j
public class DefaultCommandExecutionHandle<T> implements CommandExecutionHandle<T> {

    private final PublishProcessor<T> processor; // 使用 PublishProcessor 作为内部发布器
    private final CompletableFuture<List<T>> future; // 聚合所有结果的 Future
    private final List<T> results; // 收集中间结果

    public DefaultCommandExecutionHandle(Runloop runloop) {
        this.processor = PublishProcessor.create();
        this.future = new CompletableFuture<>();
        this.results = new ArrayList<>();

        // 订阅 processor，当所有 T 都发布完毕后，完成 future
        // 确保结果的收集在 Runloop 线程上进行，或者在安全的线程上
        processor.observeOn(Schedulers.from(runloop::postTask)) // 使用 Runloop 的调度器
                .subscribe(new FlowableSubscriber<T>() {
                    private Subscription upstreamSubscription;

                    @Override
                    public void onSubscribe(Subscription s) {
                        upstreamSubscription = s; // 直接赋值，无需类型转换
                        s.request(Long.MAX_VALUE); // 请求所有数据
                    }

                    @Override
                    public void onNext(T item) {
                        results.add(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        future.completeExceptionally(t); // 发生错误，Future 以异常结束
                        if (upstreamSubscription != null) {
                            upstreamSubscription.cancel(); // 取消订阅
                        }
                    }

                    @Override
                    public void onComplete() {
                        future.complete(results); // 使用收集到的所有结果完成 Future
                    }
                });
    }

    @Override
    public Flowable<T> toFlowable() {
        return processor;
    }

    @Override
    public CompletableFuture<List<T>> toCompletedFuture() {
        return future;
    }

    @Override
    public void submit(T item) {
        processor.onNext(item);
    }

    @Override
    public void close() {
        processor.onComplete();
    }

    @Override
    public void closeExceptionally(Throwable error) {
        processor.onError(error);
    }

    @Override
    public CommandExecutionHandle<T> onRunloop(Runloop targetRunloop) {
        // 这个方法现在需要创建一个新的 CommandExecutionHandle，并在新的 Runloop 上调度事件
        DefaultCommandExecutionHandle<T> newHandle = new DefaultCommandExecutionHandle<>(targetRunloop);
        // 将当前 handle 的事件管道到新的 handle
        processor.subscribeOn(Schedulers.from(targetRunloop::postTask))
                .observeOn(Schedulers.from(targetRunloop::postTask))
                .subscribe(new FlowableSubscriber<T>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(T item) {
                        newHandle.submit(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        newHandle.closeExceptionally(t);
                    }

                    @Override
                    public void onComplete() {
                        newHandle.close();
                    }
                });
        return newHandle;
    }

    // 内部 RunloopShiftingProcessor 不再需要，逻辑已整合或简化
}
