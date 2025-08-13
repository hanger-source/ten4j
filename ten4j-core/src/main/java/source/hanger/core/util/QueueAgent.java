package source.hanger.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

/**
 * 单虚拟线程事件循环
 * - ManyToOneConcurrentArrayQueue<T> 存事件
 * - register() 注册事件消费者
 * - post() 投递事件
 * - AgentRunner + IdleStrategy 控制循环
 */
@Slf4j
public class QueueAgent<T> {

    private static final int DEFAULT_CAPACITY = 1024;
    private static final int DEFAULT_BATCH = 64;

    private final ManyToOneConcurrentArrayQueue<T> queue;
    private final List<Consumer<T>> consumers = new ArrayList<>();
    private final int batchSize;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    /**
     * 用来表示 中断
     * 因为 AgentRunner 线程中断后 无法响应中断
     */
    private final AtomicBoolean interruptRequest = new AtomicBoolean(false);

    private final String name;
    private volatile Thread coreThread;
    private AgentRunner runner;

    private QueueAgent(String name, int capacity, int batchSize) {
        this.queue = new ManyToOneConcurrentArrayQueue<>(capacity);
        this.batchSize = batchSize;
        this.name = name;
    }

    public static <E> QueueAgent<E> create(String name) {
        return new QueueAgent<>(name, DEFAULT_CAPACITY, DEFAULT_BATCH);
    }

    public QueueAgent<T> subscribe(Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        consumers.add(consumer);
        return this;
    }

    public boolean offer(T event) {
        Objects.requireNonNull(event, "event");
        if (!running.get() || shuttingDown.get()) {
            return false;
        }
        boolean ok = queue.offer(event);
        if (ok) {
            wakeup();
        }
        return ok;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        shuttingDown.set(false);

        IdleStrategy idle = new BackoffIdleStrategy(
            1, 1,
            TimeUnit.NANOSECONDS.toNanos(50),
            TimeUnit.MICROSECONDS.toNanos(100));

        runner = new AgentRunner(
            idle,
            ex -> log.error("Uncaught in runloop", ex),
            null,
            new CoreAgent());

        coreThread = Thread.ofVirtual().name("QueueAgent-vt-%s".formatted(name), 0).start(runner);
        log.info("Runloop started on {}", coreThread);
    }

    public void clean() {
        log.info("[{}] Flushing queue...", name);
        // 1. 清空队列中所有待处理的事件
        while (queue.poll() != null) {
            // Continue polling until the queue is empty
        }
        log.info("[{}] Flush complete.", name);
    }

    public void interrupt() {
        // 设置标志，表示请求中断当前工作
        if (interruptRequest.compareAndSet(false, true)) {
            log.info("[{}] Interrupt request sent.", name);
            // 唤醒核心线程，让它能立即检查到这个标志
            wakeup();
        }
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        shuttingDown.set(true);
        try {
            if (runner != null) {
                runner.close();
            }
        } catch (Exception e) {
            log.error("Error closing runner", e);
        }
        wakeup();
        joinCoreThread();
        log.info("Runloop stopped.");
    }

    private void wakeup() {
        Thread t = coreThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    private void joinCoreThread() {
        try {
            if (coreThread != null && coreThread.isAlive()) {
                coreThread.join(2000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private class CoreAgent implements Agent {
        @Override
        public String roleName() {
            return "runloop-core";
        }

        @Override
        public int doWork() {
            int work = 0;

            // 检查中断请求
            if (interruptRequest.get()) {
                // 如果有中断请求，则清除标志并返回
                interruptRequest.set(false);
                log.info("[{}] Handling interrupt request, stopping current batch.", name);
                return 0; // 返回 0，触发 IdleStrategy，线程进入等待状态
            }

            for (int i = 0; i < batchSize; i++) {
                T event = queue.poll();
                if (event == null) {
                    break;
                }

                for (Consumer<T> consumer : consumers) {
                    try {
                        consumer.accept(event);
                    } catch (Throwable e) {
                        log.error("Consumer error", e);
                    }
                }
                work++;
            }
            return work;
        }
    }
}
