package source.hanger.core.extension.bailian.task;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import lombok.extern.slf4j.Slf4j;
import org.agrona.DeadlineTimerWheel;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.SystemEpochClock;

/**
 * 百炼任务的单线程执行器。
 * 负责管理任务队列，在单个工作线程中执行任务，并处理任务的完成、失败和超时回调。
 */
@Slf4j
public class BailianPollingTaskRunner {

    private static final int DEFAULT_QUEUE_CAPACITY = 128;

    private final ManyToOneConcurrentArrayQueue<TaskWrapper<?>> taskQueue;

    /**
     * 回调执行器：
     * - 并发回调（每个回调一个虚拟线程）更符合“回调不阻塞轮询”的目标：
     */
    private final ExecutorService callbackExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final PollingAgent pollingAgent;

    // 新增：命令队列，用于调度计时器操作和取消操作，确保在PollingAgent线程中执行
    private final ManyToOneConcurrentArrayQueue<Runnable> commandQueue;

    // 任务ID -> 定时器ID，用于快速查找和取消 (多线程访问)
    private final ConcurrentHashMap<String, Long> taskIdToTimerId;

    // 定时器ID -> 任务，用于在计时器到期时获取任务 (单线程访问，仅PollingAgent线程)
    private final Long2ObjectHashMap<DelayedTaskRequeue> activeTimeouts;

    private final DeadlineTimerWheel timerWheel;
    private AgentRunner agentRunner;
    private volatile Thread coreThread;

    public BailianPollingTaskRunner(String name) {
        Objects.requireNonNull(name, "name");

        this.taskQueue = new ManyToOneConcurrentArrayQueue<>(DEFAULT_QUEUE_CAPACITY);

        // 并发回调（每个回调一个虚拟线程）
        this.callbackExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("BailianPollingTaskRunner-%s-Callback-%s".formatted(name, "%d")).factory()
        );

        this.pollingAgent = new PollingAgent(name);
        this.commandQueue = new ManyToOneConcurrentArrayQueue<>(DEFAULT_QUEUE_CAPACITY); // 初始化命令队列
        this.taskIdToTimerId = new ConcurrentHashMap<>(); // 初始化任务ID到定时器ID的映射
        this.activeTimeouts = new Long2ObjectHashMap<>(); // 初始化活动超时任务的映射

        // ===== 正确初始化 DeadlineTimerWheel：统一为“毫秒” =====
        final SystemEpochClock clock = SystemEpochClock.INSTANCE; // 毫秒
        this.timerWheel = new DeadlineTimerWheel(
            TimeUnit.MILLISECONDS,      // 与 SystemEpochClock 对齐
            clock.time(),               // startTime：毫秒
            1,                          // tickResolution：1 毫秒
            512,                        // ticksPerWheel：2 的幂
            1024                        // initialTickAllocation：2 的幂
        );

        start();
    }

    /**
     * 提交一个百炼查询任务。
     *
     * @param task    要提交的任务
     * @param taskId  任务的唯一标识符
     * @param timeout 任务的总超时时间
     * @param <T>     任务结果类型
     */
    public <T> void submit(
            BailianPollingTask<T> task,
            String taskId,
            Duration timeout,
            Duration pollingInterval) { // 新增：轮询间隔
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(taskId, "taskId");

        if (!canAcceptTask()) {
            log.warn("[BailianPollingTaskRunner] not running or shutting down, rejecting task: {}", taskId);
            return;
        }

        boolean success = taskQueue.offer(new TaskWrapper<>(task, taskId, pollingInterval)); // 传递 pollingInterval
        if (!success) {
            log.warn("[BailianPollingTaskRunner] queue full, task dropped: {}", taskId);
            return;
        }

        // 如果有超时，将调度计时器的操作提交到命令队列
        if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
            final long deadlineMs = SystemEpochClock.INSTANCE.time() + timeout.toMillis();
            commandQueue.offer(() -> {
                if (running.get() && !shuttingDown.get()) { // 确保执行器仍然活跃
                    long timerId = timerWheel.scheduleTimer(deadlineMs);
                    activeTimeouts.put(timerId, new DelayedTaskRequeue(taskId, task, new TaskWrapper<>(task, taskId, pollingInterval), TimeoutType.TOTAL_TIMEOUT)); // 存储任务到activeTimeouts，使用新的记录
                    taskIdToTimerId.put(taskId, timerId); // 存储 taskId 到 timerId 的映射
                    log.debug("[BailianPollingTaskRunner] Scheduled timeout for taskId: {} with timerId: {}", taskId, timerId);
                } else {
                    log.warn("[BailianPollingTaskRunner] Runner not active, not scheduling timeout for taskId: {}", taskId);
                }
            });
            wakeup(); // 唤醒 polling 线程以处理新命令
        }

    }

    private void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[BailianPollingTaskRunner] already running.");
            return;
        }
        shuttingDown.set(false);

        IdleStrategy idleStrategy = new BackoffIdleStrategy(
            1, 1,
            TimeUnit.NANOSECONDS.toNanos(50),
            TimeUnit.MICROSECONDS.toNanos(100)
        );

        agentRunner = new AgentRunner(
            idleStrategy,
            ex -> log.error("[BailianPollingTaskRunner] uncaught error", ex),
            null,
            pollingAgent
        );

        coreThread = createCoreThread(agentRunner);
        log.info("[BailianPollingTaskRunner] started on thread: {}", coreThread.getName());
    }

    private void wakeup() {
        Thread t = coreThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    private boolean canAcceptTask() {
        if (shuttingDown.get()) {
            return false;
        }
        return running.get();
    }

    private void tryCloseAgentRunner() {
        if (agentRunner != null) {
            try {
                agentRunner.close();
            } catch (Exception e) {
                log.error("[BailianPollingTaskRunner] Error closing AgentRunner", e);
            }
        }
    }

    private void drainRemainingTasks() {
        TaskWrapper<?> taskWrapper;
        while ((taskWrapper = taskQueue.poll()) != null) {
            // 关闭阶段：不再调度新的超时，只把剩余任务做一次收尾处理
            BailianPollingTask<?> r = taskWrapper.task();
            String taskId = taskWrapper.taskId();
            PollingResult<?> pollingResult;
            try {
                pollingResult = r.execute(); // 执行任务，期待不抛出检查型异常
            } catch (Throwable e) {
                // 如果任务执行抛出异常，视为任务失败
                log.error("[BailianPollingTaskRunner] Task {} execution failed during drainRemainingTasks: {}", taskId, e.getMessage(), e);
                callbackExecutor.submit(() -> r.onFailure(e)); // 在虚拟线程中调用 onFailure 回调
                continue; // 处理下一个任务
            }

            callbackExecutor.submit(() -> {
                if (pollingResult.completed) {
                    // 任务完成即取消超时
                    cancelTimeout(taskId); // 提交取消命令
                    @SuppressWarnings("unchecked")
                    BailianPollingTask<Object> cast = (BailianPollingTask<Object>) r;
                    cast.onComplete(pollingResult.result);
                } else if (pollingResult.error != null) {
                    cancelTimeout(taskId); // 提交取消命令
                    r.onFailure(pollingResult.error);
                } else if (pollingResult.needsRepoll) {
                    // 关闭阶段不再重试
                    log.warn("[BailianPollingTaskRunner] is shutting down, not re-polling task: {}", taskId);
                }
            });
        }
    }

    private void joinCoreThread() {
        try {
            if (coreThread != null && coreThread.isAlive()) {
                coreThread.join(TimeUnit.SECONDS.toMillis(3));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[BailianPollingTaskRunner] shutdown interrupted.");
        }
    }

    private Thread createCoreThread(Runnable task) {
        Thread t = Thread.ofVirtual().name("%s-Core".formatted(pollingAgent.roleName())).start(() -> {
            try {
                task.run();
            } finally {
                // 需要时在此做清理
            }
        });
        t.setUncaughtExceptionHandler((_, ex) -> log.error("Core thread error", ex));
        return t;
    }

    /**
     * 关闭任务执行器。
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            log.warn("[BailianPollingTaskRunner] not running.");
            return;
        }
        shuttingDown.set(true);

        // 确保所有待处理的计时器命令被执行
        drainCommands();

        // 先停 AgentRunner（停止 doWork 循环）
        tryCloseAgentRunner();

        // 清空队列里的剩余任务
        drainRemainingTasks();

        // 唤醒并等待核心线程退出
        wakeup();
        joinCoreThread();

        // 关闭回调执行器
        callbackExecutor.shutdown();
        try {
            if (!callbackExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[BailianPollingTaskRunner] callbackExecutor did not terminate in time.");
                callbackExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[BailianPollingTaskRunner] Interrupted while waiting for BailianPollingTaskRunner to terminate.");
            callbackExecutor.shutdownNow();
        }

        log.info("[BailianPollingTaskRunner] shutdown complete.");
    }

    private void cancelTimeout(String taskId) {
        // 将取消计时器的操作提交到命令队列
        commandQueue.offer(() -> {
            Long timerId = taskIdToTimerId.remove(taskId);
            if (timerId != null) {
                timerWheel.cancelTimer(timerId); // 取消DeadlineTimerWheel中的计时器
                activeTimeouts.remove(timerId); // 从activeTimeouts中移除
                log.debug("[BailianPollingTaskRunner] Cancelled timeout for taskId: {} with timerId: {}", taskId, timerId);
            } else {
                log.debug("[BailianPollingTaskRunner] No active timeout found for taskId: {}", taskId);
            }
        });
        wakeup(); // 唤醒 polling 线程以处理取消命令
    }

    private void drainCommands() {
        Runnable command;
        while ((command = commandQueue.poll()) != null) {
            try {
                command.run();
            } catch (Exception e) {
                log.error("[BailianPollingTaskRunner] Error draining command during shutdown", e);
            }
        }
    }

    /**
     * 封装轮询任务的执行结果。
     * @param <T> 任务结果类型
     */
    public static class PollingResult<T> {
        private final boolean completed;
        private final T result;
        private final Throwable error;
        private final boolean needsRepoll;

        private PollingResult(boolean completed, T result, Throwable error, boolean needsRepoll) {
            this.completed = completed;
            this.result = result;
            this.error = error;
            this.needsRepoll = needsRepoll;
        }

        /**
         * 任务成功完成。
         * @param result 任务结果
         */
        public static <T> PollingResult<T> success(T result) {
            return new PollingResult<>(true, result, null, false);
        }

        /**
         * 任务执行失败。
         * @param error 参数: 错误信息
         */
        public static <T> PollingResult<T> error(Throwable error) {
            return new PollingResult<>(false, null, error, false);
        }

        /**
         * 任务未完成，需要继续轮询。
         */
        public static <T> PollingResult<T> needsRepoll() {
            return new PollingResult<>(false, null, null, true);
        }
    }

    // 用于在 activeTimeouts 中存储 taskId 和 BailianPollingTask 的记录
    private record DelayedTaskRequeue(String taskId, BailianPollingTask<?> task, TaskWrapper<?> originalTaskWrapper, TimeoutType timeoutType) {}

    /**
     * @param pollingInterval 新增：轮询间隔
     */
    private record TaskWrapper<T>(BailianPollingTask<T> task, String taskId, Duration pollingInterval) {

    }

    private enum TimeoutType {
        POLLING_INTERVAL,
        TOTAL_TIMEOUT
    }

    private class PollingAgent implements Agent {
        private final String name;

        PollingAgent(String name) {
            this.name = name;
        }

        @Override
        public String roleName() {
            return "BailianPollingTaskRunner-%s".formatted(name);
        }

        @Override
        public int doWork() {
            long nowMs = SystemEpochClock.INSTANCE.time();

            int workCount = 0;

            // 1. 处理命令队列中的命令 (如调度/取消计时器)
            Runnable command;
            while ((command = commandQueue.poll()) != null) {
                try {
                    command.run();
                    workCount++;
                } catch (Exception e) {
                    log.error("[BailianPollingTaskRunner] Error processing command", e);
                }
            }

            // 2. 处理到期计时器
            final int expiredTimers = timerWheel.poll(nowMs, (timeUnit, now, timerId) -> {
                DelayedTaskRequeue delayedRequeue = activeTimeouts.remove(timerId);
                if (delayedRequeue != null) {
                    // 从 taskIdToTimerId 映射中也移除，因为计时器已到期
                    taskIdToTimerId.remove(delayedRequeue.taskId());

                    if (delayedRequeue.timeoutType() == TimeoutType.POLLING_INTERVAL) {
                        log.debug("[BailianPollingTaskRunner] Re-queueing task {} after polling interval. Timer ID: {}", delayedRequeue.taskId(), timerId);
                        // 将原始任务重新入队 taskQueue
                        taskQueue.offer(delayedRequeue.originalTaskWrapper());
                        wakeup(); // 唤醒以再次处理
                    } else if (delayedRequeue.timeoutType() == TimeoutType.TOTAL_TIMEOUT) {
                        log.warn("[BailianPollingTaskRunner] Task {} total timeout, calling onTimeout. Timer ID: {}", delayedRequeue.taskId(), timerId);
                        // 总任务超时，触发 onTimeout 回调
                        callbackExecutor.submit(() -> delayedRequeue.task().onTimeout());
                    }
                    return true; // 消费计时器
                }
                log.debug("[BailianPollingTaskRunner] Expired timer with ID {} found but no corresponding delayed re-queue task. Already handled or cancelled?", timerId);
                return true; // 即使找不到任务，也消费计时器（可能任务已完成并被取消）
            }, Integer.MAX_VALUE); // 轮询所有到期计时器
            workCount += expiredTimers;

            // 3. 处理任务队列中的轮询任务
            TaskWrapper<?> taskWrapper = taskQueue.poll();
            if (taskWrapper != null) {
                BailianPollingTask<?> task = taskWrapper.task();
                String taskId = taskWrapper.taskId();

                log.info("[BailianPollingTaskRunner] processing task: {}", taskId);
                PollingResult<?> pollingResult;
                try {
                    pollingResult = task.execute(); // 执行任务，期待不抛出检查型异常
                } catch (Throwable e) {
                    // 如果任务执行抛出异常，视为任务失败
                    log.error("[BailianPollingTaskRunner] Task {} execution failed with exception: {}", taskId, e.getMessage(), e);
                    cancelTimeout(taskId); // 任务失败，取消超时
                    callbackExecutor.submit(() -> task.onFailure(e)); // 在虚拟线程中调用 onFailure 回调
                    workCount++;
                    return workCount; // 处理下一个任务
                }

                if (pollingResult.completed || pollingResult.error != null) {
                    // 任务完成或失败，立即取消计时器并清理相关映射
                    log.info("[BailianPollingTaskRunner] Task {} completed or failed, cancelling timeout and cleaning up.", taskId);
                    cancelTimeout(taskId); // 确保取消操作立即发生
                }

                // 无论是否重新轮询，都将回调提交到回调执行器
                callbackExecutor.submit(() -> {
                    if (pollingResult.completed) {
                        log.info("[BailianPollingTaskRunner] Calling onComplete for task {}.", taskId);
                        @SuppressWarnings("unchecked")
                        BailianPollingTask<Object> cast = (BailianPollingTask<Object>) task;
                        cast.onComplete(pollingResult.result);
                    } else if (pollingResult.error != null) {
                        log.info("[BailianPollingTaskRunner] Calling onFailure for task {}.", taskId);
                        task.onFailure(pollingResult.error);
                    }
                    // 如果需要重新轮询，这里的逻辑已经在外面处理了，不需要在这里再次入队
                });

                if (pollingResult.needsRepoll) {
                    // 如果任务未完成且需要继续轮询
                    Duration currentPollingInterval = taskWrapper.pollingInterval();
                    if (currentPollingInterval != null && !currentPollingInterval.isNegative() && !currentPollingInterval.isZero()) {
                        // 调度延迟重新入队
                        final long reEnqueueDeadlineMs = nowMs + currentPollingInterval.toMillis();
                        commandQueue.offer(() -> {
                            if (running.get() && !shuttingDown.get()) {
                                // 在重新调度之前，先检查是否已存在一个计时器，并取消它以避免重复
                                Long existingTimerId = taskIdToTimerId.get(taskId);
                                if (existingTimerId != null) {
                                    timerWheel.cancelTimer(existingTimerId);
                                    activeTimeouts.remove(existingTimerId);
                                    log.debug("[BailianPollingTaskRunner] Existing timer {} cancelled for taskId: {} before re-scheduling.", existingTimerId, taskId);
                                }
                                long timerId = timerWheel.scheduleTimer(reEnqueueDeadlineMs);
                                // 存储原始的 TaskWrapper，以便在计时器到期时重新入队
                                activeTimeouts.put(timerId, new DelayedTaskRequeue(taskId, task, taskWrapper, TimeoutType.POLLING_INTERVAL));
                                // 更新 taskIdToTimerId 映射为新的延迟重新入队计时器 ID
                                taskIdToTimerId.put(taskId, timerId);
                                log.debug("[BailianPollingTaskRunner] Scheduled delayed re-queue for taskId: {} with timerId: {} (interval {}ms)", taskId, timerId, currentPollingInterval.toMillis());
                            } else {
                                log.warn("[BailianPollingTaskRunner] Runner not active, not scheduling delayed re-queue for taskId: {}.", taskId);
                            }
                        });
                        wakeup(); // 唤醒 polling 线程以处理新的调度命令
                    } else {
                        // 没有指定轮询间隔或间隔无效，立即重新入队
                        if (!shuttingDown.get() && running.get()) {
                            log.info("[BailianPollingTaskRunner] No polling interval, immediately re-queuing task {}.", taskId);
                            taskQueue.offer(taskWrapper); // 重新入队包装器
                            wakeup(); // 唤醒以再次处理
                        } else {
                            log.warn("[BailianPollingTaskRunner] is shutting down, not re-polling task: {}.", taskId);
                        }
                    }
                }
                workCount++;
            }
            return workCount;
        }

        @Override
        public void onStart() {
            log.info("[BailianPollingTaskRunner] started", roleName());
        }

        @Override
        public void onClose() {
            log.info("[BailianPollingTaskRunner] closed", roleName());
        }
    }
}
