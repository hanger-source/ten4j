package source.hanger.core.runloop;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

/**
 * Runloop 类负责线程管理和任务调度，对齐 C 语言的 ten_runloop。
 * 基于 Agrona AgentRunner 实现单线程事件循环，处理内部任务和work Agent 列表。
 *
 * 特性：
 * - 批量消费内部任务（可配置批量大小）
 * - 使用 BackoffIdleStrategy（折中自旋 -> yield -> sleep）
 * - 提交任务后唤醒 runloop 线程以提高响应性
 * - 生命周期 onStart / onClose 会转发到注册的work Agent
 */
@Slf4j
public class Runloop {

    public static final int DEFAULT_INTERNAL_QUEUE_CAPACITY = 1024;
    private static final int DEFAULT_INTERNAL_TASK_BATCH = 64;
    public final AtomicBoolean running = new AtomicBoolean(false);
    public final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final ManyToOneConcurrentArrayQueue<Runnable> taskQueue;
    private final Agent workAgent;
    private final LoopAgent coreAgent;
    private final int internalTaskBatchSize;
    private final ThreadLocal<Runloop> currentRunloopThreadLocal = new ThreadLocal<>();
    private final List<Runnable> tasks;

    /**
     * 单一虚拟线程 保证队列消费顺序
     */
    private final ExecutorService virtualThreadExecutor;

    private AgentRunner agentRunner;
    @Getter
    private volatile Thread coreThread;

    @Setter
    private volatile Runnable externalEventSourceNotifier;

    private Runloop(String name, Agent workAgent, int queueCapacity, int batchSize) {
        Objects.requireNonNull(name, "name");
        int capacity = adjustCapacity(queueCapacity);
        this.taskQueue = new ManyToOneConcurrentArrayQueue<>(capacity);
        this.internalTaskBatchSize = Math.max(1, batchSize);
        this.workAgent = workAgent;
        this.coreAgent = new LoopAgent(name);
        tasks = new CopyOnWriteArrayList<>();
        this.virtualThreadExecutor = Executors
                .newSingleThreadExecutor(Thread.ofVirtual().name("Runloop-%s-vt".formatted(name), 0).factory());
    }

    public static Runloop createRunloopWithWorker(String name, Agent workAgent) {
        return new Runloop(name, workAgent, DEFAULT_INTERNAL_QUEUE_CAPACITY, DEFAULT_INTERNAL_TASK_BATCH);
    }

    public static Runloop createRunloop(String name) {
        return new Runloop(name, null, DEFAULT_INTERNAL_QUEUE_CAPACITY, DEFAULT_INTERNAL_TASK_BATCH);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Runloop already running.");
            return;
        }
        shuttingDown.set(false);

        IdleStrategy idleStrategy = new BackoffIdleStrategy(
                1, 1,
                TimeUnit.NANOSECONDS.toNanos(50),
                TimeUnit.MICROSECONDS.toNanos(100));

        agentRunner = new AgentRunner(
                idleStrategy,
                ex -> log.error("Runloop uncaught error", ex),
                null,
                coreAgent);

        coreThread = createCoreThread(agentRunner);
        log.info("Runloop started on thread: {}", coreThread.getName());
    }

    public boolean postTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!canAcceptTask()) {
            return false;
        }
        boolean success = taskQueue.offer(task);
        if (!success) {
            log.warn("Runloop queue full, task dropped.");
            return false;
        }
        wakeup();
        return true;
    }

    public void wakeup() {
        Thread t = coreThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            log.warn("Runloop not running.");
            return;
        }
        shuttingDown.set(true);
        tryCloseAgentRunner();
        drainRemainingTasks();
        wakeup();
        joinCoreThread();
        // Add shutdown for virtualThreadExecutor
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("VirtualThreadExecutor did not terminate in time.");
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for VirtualThreadExecutor to terminate.");
            virtualThreadExecutor.shutdownNow();
        }
        log.info("Runloop shutdown complete.");
    }

    public boolean isNotCurrentThread() {
        return Thread.currentThread() != coreThread;
    }

    private boolean canAcceptTask() {
        if (shuttingDown.get()) {
            log.warn("Runloop shutting down, rejecting task.");
            return false;
        }
        if (!running.get()) {
            log.warn("Runloop not running, rejecting task.");
            return false;
        }
        return true;
    }

    private void tryCloseAgentRunner() {
        if (agentRunner != null) {
            try {
                agentRunner.close();
            } catch (Exception e) {
                log.error("Error closing AgentRunner", e);
            }
        }
    }

    private void drainRemainingTasks() {
        Runnable r;
        while ((r = taskQueue.poll()) != null) {
            try {
                virtualThreadExecutor.submit(r);
            } catch (Throwable e) {
                log.error("Error executing remaining task", e);
            }
        }
    }

    private void joinCoreThread() {
        try {
            if (coreThread != null && coreThread.isAlive()) {
                coreThread.join(TimeUnit.SECONDS.toMillis(3));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Runloop shutdown interrupted");
        }
    }

    private Thread createCoreThread(Runnable task) {
        Thread t = new Thread(() -> {
            currentRunloopThreadLocal.set(this);
            try {
                task.run();
            } finally {
                currentRunloopThreadLocal.remove();
            }
        }, "%s-Core".formatted(coreAgent.roleName()));
        t.setDaemon(false);
        t.setUncaughtExceptionHandler((_, ex) -> log.error("Core thread error", ex));
        t.start();
        return t;
    }

    private int adjustCapacity(int requestedCapacity) {
        int cap = Math.max(1, Integer.highestOneBit(requestedCapacity));
        return (cap < requestedCapacity) ? cap << 1 : cap;
    }

    public void registerTask(Runnable task) {
        tasks.add(task);
    }

    private class LoopAgent implements Agent {
        private final String name;

        LoopAgent(String name) {
            this.name = name;
        }

        @Override
        public String roleName() {
            return "Runloop-%s".formatted(name);
        }

        @Override
        public int doWork() {
            int workDone = 0;
            // 批量处理内部任务
            for (int i = 0; i < internalTaskBatchSize; i++) {
                Runnable r = taskQueue.poll();
                if (r == null) {
                    break;
                }
                safeRun(r);
                workDone++;
            }
            for (Runnable task : tasks) {
                task.run();
                workDone++;
            }
            // 调用外部 workAgent
            if (workAgent != null) {
                try {
                    workDone += workAgent.doWork();
                } catch (Throwable e) {
                    log.error("WorkAgent {} error", name, e);
                }
            }
            return workDone;
        }

        @Override
        public void onStart() {
            log.info("{} started", roleName());
            if (workAgent != null) {
                safeRun(workAgent::onStart);
            }
        }

        @Override
        public void onClose() {
            log.info("{} closed", roleName());
            if (workAgent != null) {
                safeRun(workAgent::onClose);
            }
        }

        private void safeRun(Runnable task) {
            try {
                virtualThreadExecutor.submit(task);
            } catch (Throwable e) {
                log.error("Error executing task", e);
            }
        }
    }
}