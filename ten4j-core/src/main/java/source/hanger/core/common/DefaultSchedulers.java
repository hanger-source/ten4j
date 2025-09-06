package source.hanger.core.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public class DefaultSchedulers {

    private static final ExecutorService IO_OFFLOAD_EXECUTOR =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
            .name("IO-offload-worker-vt", 0).factory());

    /**
     * 当 httpclient 或 websocket 的核心线程完成 I/O 任务后，它会立即被释放
     * 而不需要等待后续结果处理逻辑执行完毕。避免宝贵的IO核心线程被阻塞。
     */
    public static final Scheduler IO_OFFLOAD_SCHEDULER =
        Schedulers.from(IO_OFFLOAD_EXECUTOR);
}
