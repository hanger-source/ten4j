package source.hanger.core.util;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.schedulers.Schedulers;

public class RxStreamUtils {

    /**
     * 订阅流中的流，保证多条流顺序执行，内部使用指定线程池调度
     *
     * @param streamProcessor 负责接收多条流的发布器
     * @param executorService 自定义线程池，用于线程调度
     * @param onNext          单个元素消费逻辑
     * @param onError         异常处理
     * @param onComplete      完成回调
     * @param <T>             流内部元素类型
     * @return Disposable 可取消订阅
     */
    public static <T> Disposable subscribeOrderedStream(
        FlowableProcessor<Flowable<T>> streamProcessor,
        ExecutorService executorService,
        Consumer<T> onNext,
        Consumer<Throwable> onError,
        Runnable onComplete) {

        return streamProcessor
            .onBackpressureBuffer()
            .concatMap(flowable -> flowable
                .subscribeOn(Schedulers.from(executorService))
                .observeOn(Schedulers.from(executorService))
            )
            .subscribe(
                onNext::accept,
                onError::accept,
                onComplete::run
            );
    }
}
