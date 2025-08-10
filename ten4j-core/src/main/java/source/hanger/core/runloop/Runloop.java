package source.hanger.core.runloop;

import java.util.Objects;
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

    public static final int DEFAULT_INTERNAL_QUEUE_CAPACITY = 1024; // Ensure public static final
    // private static final Logger log = LoggerFactory.getLogger(Runloop.class); //
    // Explicitly declare log
    private static final int DEFAULT_INTERNAL_TASK_BATCH = 64;

    private final ManyToOneConcurrentArrayQueue<Runnable> taskQueue;
    private final Agent workAgent;
    private final LoopAgent coreAgent;
    private final int internalTaskBatchSize;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false); // 新增：指示 runloop 是否正在关闭
    // ThreadLocal 用于存储当前线程的 Runloop 实例，支持线程亲和性检查
    private final ThreadLocal<Runloop> currentRunloopThreadLocal = new ThreadLocal<>();
    private AgentRunner agentRunner;
    private volatile boolean running = false;
    @Getter
    private volatile Thread coreThread;
    /**
     * -- SETTER --
     * 设置外部唤醒器（可选）。当外部有新事件时，可调用该 notifier 或直接调用 runloop.wakeup()。
     */
    // 可选的外部唤醒器（外部可在有新事件时调用该 Runnable）
    @Setter
    private volatile Runnable externalEventSourceNotifier;

    /**
     * 使用默认配置
     *
     * @param name runloop 名称（用于线程名）
     */
    private Runloop(String name, Agent workAgent) {
        this(name, workAgent, DEFAULT_INTERNAL_QUEUE_CAPACITY, DEFAULT_INTERNAL_TASK_BATCH);
    }

    /**
     * 可配置构造
     *
     * @param name                  名称
     * @param requestedCapacity     初始容量（会向上调整为 2 的幂）
     * @param internalTaskBatchSize 每轮最多处理多少个内部任务
     */
    private Runloop(String name, Agent workAgent, int requestedCapacity, int internalTaskBatchSize) {
        Objects.requireNonNull(name, "name");

        int capacity = Math.max(1, Integer.highestOneBit(requestedCapacity));
        if (capacity < requestedCapacity) {
            capacity <<= 1;
        }
        taskQueue = new ManyToOneConcurrentArrayQueue<>(capacity);
        this.internalTaskBatchSize = Math.max(1, internalTaskBatchSize);
        this.workAgent = workAgent;
        coreAgent = new LoopAgent(name);
    }

    public static Runloop createRunloopWithWorker(String name, Agent workAgent) {
        return new Runloop(name, workAgent);
    }

    public static Runloop createRunloop(String name) {
        return new Runloop(name, null);
    }

    /**
     * 启动 Runloop（起一个线程运行 AgentRunner）。
     */
    public void start() {
        if (running) {
            log.warn("Runloop already started.");
            return;
        }
        running = true;
        shuttingDown.set(false); // 确保启动时不是关闭状态

        IdleStrategy idleStrategy = new BackoffIdleStrategy(
                1, // maxSpins
                1, // maxYields
                TimeUnit.NANOSECONDS.toNanos(50), // minParkPeriodNs
                TimeUnit.MICROSECONDS.toNanos(100) // maxParkPeriodNs
        );

        agentRunner = new AgentRunner(
                idleStrategy,
                (throwable) -> log.error("Runloop AgentRunner 未捕获异常", throwable),
                null,
                coreAgent);

        coreThread = useThread();

        log.info("Runloop started. Thread: {}", coreThread.getName());
    }

    private Thread useThread() {
        Thread agentThread = new Thread(agentRunner, "%s-RunLoop".formatted(coreAgent.roleName())) {
            @Override
            public void run() {
                currentRunloopThreadLocal.set(Runloop.this); // 在 Runloop 线程中设置 ThreadLocal
                super.run();
                currentRunloopThreadLocal.remove(); // 线程退出时清理
            }
        };
        agentThread.setDaemon(false);
        agentThread.setUncaughtExceptionHandler((thread, ex) -> log.error("Runloop AgentRunner 线程未捕获异常", ex));
        agentThread.start();
        return agentThread;
    }

    /**
     * 提交任务到内部队列（会在 Runloop 专属线程上执行）。
     *
     * @param task 非空 Runnable
     * @return true 表示入队成功，false 表示队列满或尚未启动
     */
    public boolean postTask(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        if (shuttingDown.get()) { // 在 running 之前检查 shuttingDown
            log.warn("Runloop is shutting down, task will not be accepted.");
            return false;
        }
        if (!running) {
            log.warn("Runloop is not running, task will not be executed.");
            return false;
        }

        boolean success = taskQueue.offer(task);
        if (!success) {
            log.warn("Runloop 内部任务队列已满，任务被丢弃。");
            return false;
        }

        log.debug("Runloop {}: 任务提交成功，任务哈希：{}", coreAgent.name, System.identityHashCode(task));

        // 唤醒 runloop 线程以尽快处理任务
        Thread t = coreThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
        return true;
    }

    /**
     * 唤醒 runloop（外部也可以直接调用此方法）。
     */
    public void wakeup() {
        Thread t = coreThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    /**
     * 安全关闭 Runloop。会调用 AgentRunner.close() 并等待线程退出（短超时）。
     */
    public void shutdown() {
        if (!running) {
            log.warn("Runloop is not running, no need to shut down.");
            return;
        }
        shuttingDown.set(true); // 设置正在关闭标志
        running = false; // 先设置为 false，防止新的任务入队

        try {
            if (agentRunner != null) {
                agentRunner.close();
            }
        } catch (Exception e) {
            log.error("Runloop AgentRunner close 异常", e);
        }

        // 确保所有剩余任务被处理，对齐 C 语言的语义
        while (!taskQueue.isEmpty()) {
            Runnable r = taskQueue.poll();
            if (r != null) {
                try {
                    r.run();
                } catch (Throwable e) {
                    log.error("Runloop: 关闭时执行剩余任务异常", e);
                }
            } else {
                // 如果队列突然为空，可能是被其他线程清空，或者没有更多任务了
                break;
            }
        }

        // 唤醒以确保正在 park 的线程能尽快退出
        wakeup();

        try {
            if (coreThread != null && coreThread.isAlive()) {
                coreThread.join(TimeUnit.SECONDS.toMillis(3));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Runloop shutdown 被中断");
        }

        log.info("Runloop shutdown completed.");
    }

    // 判断当前线程是否是 Runloop 的核心线程
    public boolean isNotCurrentThread() {
        return Thread.currentThread() != coreThread;
    }

    /**
     * 内部 Agent，负责合并内部队列任务与所有work Agent 的 doWork 调用。
     */
    private class LoopAgent implements Agent {
        private final String name;

        LoopAgent(String name) {
            this.name = name;
        }

        @Override
        public String roleName() {
            return "TEN-Runloop-Agent-%s".formatted(name);
        }

        @Override
        public int doWork() {
            int workDone = 0;

            // 1) 批量处理内部任务
            int processed = 0;
            Runnable r;
            while (processed < internalTaskBatchSize) {
                r = taskQueue.poll();
                if (r == null) {
                    break;
                }
                try {
                    r.run();
                } catch (Throwable e) {
                    log.error("RunloopAgent: 执行内部任务异常", e);
                }
                processed++;
                workDone++;
            }

            if (workAgent != null) {
                // 2) 调用work Agent 的 doWork()
                try {
                    int w = workAgent.doWork();
                    if (w > 0) {
                        workDone += w;
                    }
                } catch (Throwable e) {
                    try {
                        log.error("RunloopAgent: work Agent {} 执行异常", workAgent.roleName(), e);
                    } catch (Throwable ignore) {
                        // 防止 agent.roleName() 本身抛异常影响主循环
                        log.error("RunloopAgent: work Agent 执行异常 (无法获取 roleName)", e);
                    }
                }
            }

            return workDone;
        }

        @Override
        public void onStart() {
            log.info("{} started.", roleName());
            // 转发 onStart 到work Agents，保护性捕获异常
            if (workAgent != null) {
                try {
                    workAgent.onStart();
                } catch (Throwable e) {
                    try {
                        log.error("RunloopAgent: work Agent {} onStart 异常", workAgent.roleName(), e);
                    } catch (Throwable ignore) {
                        log.error("RunloopAgent: work Agent onStart 异常 (无法获取 roleName)", e);
                    }
                }
            }
        }

        @Override
        public void onClose() {
            log.info("{} closed.", roleName());
            // 移除内部队列清理，由 Runloop.shutdown() 统一处理剩余任务
            // taskQueue.clear();

            // 转发 onClose 到work Agents
            if (workAgent != null) {
                try {
                    workAgent.onClose();
                } catch (Throwable e) {
                    try {
                        log.error("RunloopAgent: work Agent {} onClose 异常", workAgent.roleName(), e);
                    } catch (Throwable ignore) {
                        log.error("RunloopAgent: work Agent onClose 异常 (无法获取 roleName)", e);
                    }
                }
            }
        }
    }
}