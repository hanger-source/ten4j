package source.hanger.core.extension.dashscope.task;

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
    private final ConcurrentHashMap<String, Long> taskIdToTotalTimeoutTimerId;

    // 新增：任务ID -> 轮询间隔定时器ID，用于快速查找和取消 (多线程访问)
    private final ConcurrentHashMap<String, Long> taskIdToPollingIntervalTimerId;

    // 新增：已取消或已超时的任务ID集合 (多线程访问)
    private final ConcurrentHashMap<String, Boolean> cancelledTasks;

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
            Thread.ofVirtual().name("BailianPollingTaskRunner-%s-Callback".formatted(name)).factory()
        );

        this.pollingAgent = new PollingAgent(name);
        this.commandQueue = new ManyToOneConcurrentArrayQueue<>(DEFAULT_QUEUE_CAPACITY); // 初始化命令队列
        this.taskIdToTotalTimeoutTimerId = new ConcurrentHashMap<>(); // 初始化任务ID到总超时定时器ID的映射
        this.taskIdToPollingIntervalTimerId = new ConcurrentHashMap<>(); // 初始化任务ID到轮询间隔定时器ID的映射
        this.activeTimeouts = new Long2ObjectHashMap<>(); // 初始化活动超时任务的映射
        this.cancelledTasks = new ConcurrentHashMap<>(); // 初始化已取消任务的映射

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
            log.warn("[{}] not running or shutting down, rejecting task: {}", pollingAgent.roleName(), taskId);
            return;
        }

        boolean success = taskQueue.offer(new TaskWrapper<>(task, taskId, pollingInterval)); // 传递 pollingInterval
        if (!success) {
            log.warn("[{}] queue full, task dropped: {}", pollingAgent.roleName(), taskId);
            return;
        }

        // 如果有超时，将调度计时器的操作提交到命令队列
        if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
            final long deadlineMs = SystemEpochClock.INSTANCE.time() + timeout.toMillis();
            commandQueue.offer(() -> {
                if (running.get() && !shuttingDown.get()) { // 确保执行器仍然活跃
                    long timerId = timerWheel.scheduleTimer(deadlineMs);
                    activeTimeouts.put(timerId, new DelayedTaskRequeue(taskId, task, new TaskWrapper<>(task, taskId, pollingInterval), TimeoutType.TOTAL_TIMEOUT)); // 存储任务到activeTimeouts，使用新的记录
                    taskIdToTotalTimeoutTimerId.put(taskId, timerId); // 存储 taskId 到 timerId 的映射
                    log.debug("[{}] Scheduled timeout for taskId: {} with timerId: {}", pollingAgent.roleName(), taskId, timerId);
                } else {
                    log.warn("[{}] Runner not active, not scheduling timeout for taskId: {}", pollingAgent.roleName(), taskId);
                }
            });
            wakeup(); // 唤醒 polling 线程以处理新命令
        }

    }

    private void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[{}] already running.", pollingAgent.roleName());
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
            ex -> log.error("[{}] uncaught error", pollingAgent.roleName(), ex),
            null,
            pollingAgent
        );

        coreThread = createCoreThread(agentRunner);
        log.info("[{}] started on thread: {}", pollingAgent.roleName(), coreThread.getName());
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
                log.error("[{}] Error closing AgentRunner", pollingAgent.roleName(), e);
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
                log.error("[{}] Task {} execution failed during drainRemainingTasks: {}", pollingAgent.roleName(), taskId, e.getMessage(), e);
                callbackExecutor.submit(() -> r.onFailure(e)); // 在虚拟线程中调用 onFailure 回调
                continue; // 处理下一个任务
            }

            callbackExecutor.submit(() -> {
                if (pollingResult.completed) {
                    // 任务完成即取消超时
                    cancelTimeout(taskId, TimeoutType.TOTAL_TIMEOUT); // 提交取消命令
                    @SuppressWarnings("unchecked")
                    BailianPollingTask<Object> cast = (BailianPollingTask<Object>) r;
                    cast.onComplete(pollingResult.result);
                } else if (pollingResult.error != null) {
                    cancelTimeout(taskId, TimeoutType.TOTAL_TIMEOUT); // 提交取消命令
                    r.onFailure(pollingResult.error);
                } else if (pollingResult.needsRepoll) {
                    // 关闭阶段不再重试
                    log.warn("[{}] is shutting down, not re-polling task: {}", pollingAgent.roleName(), taskId);
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
            log.warn("[{}] shutdown interrupted.", pollingAgent.roleName());
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
            log.warn("[{}] not running.", pollingAgent.roleName());
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
                log.warn("[{}] callbackExecutor did not terminate in time.", pollingAgent.roleName());
                callbackExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[{}] Interrupted while waiting for BailianPollingTaskRunner to terminate.", pollingAgent.roleName());
            callbackExecutor.shutdownNow();
        }

        log.info("[{}] shutdown complete.", pollingAgent.roleName());
    }

    private void cancelTimeout(String taskId, TimeoutType timeoutType) {
        // 将取消计时器的操作提交到命令队列
        commandQueue.offer(() -> {
            Long timerId = null;
            if (timeoutType == TimeoutType.TOTAL_TIMEOUT) {
                timerId = taskIdToTotalTimeoutTimerId.remove(taskId);
            } else if (timeoutType == TimeoutType.POLLING_INTERVAL) {
                timerId = taskIdToPollingIntervalTimerId.remove(taskId);
            }

            if (timerId != null) {
                timerWheel.cancelTimer(timerId); // 取消DeadlineTimerWheel中的计时器
                activeTimeouts.remove(timerId); // 从activeTimeouts中移除
                log.debug("[{}] Cancelled {} timeout for taskId: {} with timerId: {}", pollingAgent.roleName(),
                    timeoutType, taskId, timerId);
            } else {
                log.debug("[{}] No active {} timeout found for taskId: {}", pollingAgent.roleName(), timeoutType,
                    taskId);
            }

            // 检查是否所有相关计时器都已终止，如果是，则从 cancelledTasks 中移除 taskId
        });
        wakeup(); // 唤醒 polling 线程以处理取消命令
    }

    private void cleanupCancelledTask(String taskId) {
        commandQueue.offer(() -> {
            // 只有当任务的所有相关计时器都已终止时，才从 cancelledTasks 中移除 taskId
            boolean hasActiveTotalTimeout = taskIdToTotalTimeoutTimerId.containsKey(taskId);
            boolean hasActivePollingInterval = taskIdToPollingIntervalTimerId.containsKey(taskId);

            if (!hasActiveTotalTimeout && !hasActivePollingInterval) {
                cancelledTasks.remove(taskId);
                log.debug("[{}] Removed taskId: {} from cancelledTasks as all timers are terminated.", pollingAgent.roleName(), taskId);
            } else {
                log.debug("[{}] taskId: {} still has active timers (Total: {}, Polling: {}), keeping in cancelledTasks for now.", pollingAgent.roleName(), taskId, hasActiveTotalTimeout, hasActivePollingInterval);
            }
        });
        wakeup(); // 唤醒 polling 线程以处理清理命令
    }

    private void drainCommands() {
        Runnable command;
        while ((command = commandQueue.poll()) != null) {
            try {
                command.run();
            } catch (Exception e) {
                log.error("[{}] Error draining command during shutdown", pollingAgent.roleName(), e);
            }
        }
    }

    private enum TimeoutType {
        POLLING_INTERVAL,
        TOTAL_TIMEOUT
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
                    log.error("[{}] Error processing command", roleName(), e);
                }
            }

            // 2. 处理到期计时器
            final int expiredTimers = timerWheel.poll(nowMs, (timeUnit, now, timerId) -> {
                DelayedTaskRequeue delayedRequeue = activeTimeouts.remove(timerId);
                if (delayedRequeue != null) {
                    // 从 taskIdToTimerId 映射中也移除，因为计时器已到期
                    // 针对不同的超时类型，从对应的映射中移除
                    if (delayedRequeue.timeoutType() == TimeoutType.TOTAL_TIMEOUT) {
                        taskIdToTotalTimeoutTimerId.remove(delayedRequeue.taskId());
                    } else if (delayedRequeue.timeoutType() == TimeoutType.POLLING_INTERVAL) {
                        taskIdToPollingIntervalTimerId.remove(delayedRequeue.taskId());
                    }

                    if (delayedRequeue.timeoutType() == TimeoutType.POLLING_INTERVAL) {
                        // 在重新入队之前，再次检查任务是否已被取消（例如，可能已触发 TOTAL_TIMEOUT）
                        if (cancelledTasks.containsKey(delayedRequeue.taskId())) {
                            log.debug("[{}] Task {} is cancelled or timed out, skipping re-queueing after polling interval. Timer ID: {}", roleName(), delayedRequeue.taskId(), timerId);
                            cleanupCancelledTask(delayedRequeue.taskId()); // 调度清理 cancelledTasks 的命令
                            return true; // 消费计时器，但不重新入队
                        }
                        log.debug("[{}] Re-queueing task {} after polling interval. Timer ID: {}", roleName(), delayedRequeue.taskId(), timerId);
                        // 将原始任务重新入队 taskQueue
                        taskQueue.offer(delayedRequeue.originalTaskWrapper());
                        wakeup(); // 唤醒以再次处理
                    } else if (delayedRequeue.timeoutType() == TimeoutType.TOTAL_TIMEOUT) {
                        log.warn("[{}] Task {} total timeout, calling onTimeout. Timer ID: {}", roleName(), delayedRequeue.taskId(), timerId);
                        // 总任务超时，触发 onTimeout 回调
                        callbackExecutor.submit(() -> delayedRequeue.task().onTimeout());
                        cancelledTasks.put(delayedRequeue.taskId(), true); // 将任务标记为已取消
                        cancelTimeout(delayedRequeue.taskId(), TimeoutType.TOTAL_TIMEOUT); // 调度取消总超时计时器的命令
                        cleanupCancelledTask(delayedRequeue.taskId()); // 调度清理 cancelledTasks 的命令
                    }
                    return true; // 消费计时器
                }
                log.debug("[{}] Expired timer with ID {} found but no corresponding delayed re-queue task. Already handled or cancelled?", roleName(), timerId);
                return true; // 即使找不到任务，也消费计时器（可能任务已完成并被取消）
            }, Integer.MAX_VALUE); // 轮询所有到期计时器
            workCount += expiredTimers;

            // 3. 处理任务队列中的轮询任务
            TaskWrapper<?> taskWrapper = taskQueue.poll();
            if (taskWrapper != null) {
                String taskId = taskWrapper.taskId();
                if (cancelledTasks.containsKey(taskId)) {
                    log.debug("[{}] Task {} is cancelled or timed out, skipping processing.", roleName(), taskId);
                    // 仍然需要消费掉这个任务，但是不处理其逻辑
                    return ++workCount;
                }
                BailianPollingTask<?> task = taskWrapper.task();

                log.info("[{}] processing task: {}", roleName(), taskId);
                PollingResult<?> pollingResult;
                try {
                    pollingResult = task.execute(); // 执行任务，期待不抛出检查型异常
                } catch (Throwable e) {
                    // 如果任务执行抛出异常，视为任务失败
                    log.error("[{}] Task {} execution failed with exception: {}", roleName(), taskId, e.getMessage(), e);
                    cancelTimeout(taskId, TimeoutType.TOTAL_TIMEOUT); // 任务失败，取消总超时
                    cancelTimeout(taskId, TimeoutType.POLLING_INTERVAL); // 任务失败，取消轮询间隔超时
                    callbackExecutor.submit(() -> task.onFailure(e)); // 在虚拟线程中调用 onFailure 回调
                    workCount++;
                    return workCount; // 处理下一个任务
                }

                if (pollingResult.completed || pollingResult.error != null) {
                    // 任务完成或失败，立即取消计时器并清理相关映射
                    log.info("[{}] Task {} completed or failed, cancelling timeouts and cleaning up.", roleName(),
                        taskId);
                    cancelTimeout(taskId, TimeoutType.TOTAL_TIMEOUT); // 确保取消总超时操作立即发生
                    cancelTimeout(taskId, TimeoutType.POLLING_INTERVAL); // 确保取消轮询间隔超时操作立即发生
                    cancelledTasks.put(taskId, true); // 任务完成或失败，立即标记为已取消
                    cleanupCancelledTask(taskId); // 调度清理 cancelledTasks 的命令
                }

                // 无论是否重新轮询，都将回调提交到回调执行器
                callbackExecutor.submit(() -> {
                    if (pollingResult.completed) {
                        log.info("[{}] Calling onComplete for task {}.", roleName(), taskId);
                        @SuppressWarnings("unchecked")
                        BailianPollingTask<Object> cast = (BailianPollingTask<Object>) task;
                        cast.onComplete(pollingResult.result);
                    } else if (pollingResult.error != null) {
                        log.info("[{}] Calling onFailure for task {}.", roleName(), taskId);
                        task.onFailure(pollingResult.error);
                    }
                    // 如果需要重新轮询，这里的逻辑已经在外面处理了，不需要在这里再次入队
                });

                if (pollingResult.needsRepoll) {
                    // 如果任务已取消或已超时，不再重新轮询
                    if (cancelledTasks.containsKey(taskId)) {
                        log.debug("[{}] Task {} is cancelled or timed out, skipping re-polling.", roleName(), taskId);
                        return ++workCount;
                    }
                    // 如果任务未完成且需要继续轮询
                    Duration currentPollingInterval = taskWrapper.pollingInterval();
                    if (currentPollingInterval != null && !currentPollingInterval.isNegative() && !currentPollingInterval.isZero()) {
                        // 调度延迟重新入队
                        final long reEnqueueDeadlineMs = nowMs + currentPollingInterval.toMillis();
                        commandQueue.offer(() -> {
                            if (running.get() && !shuttingDown.get()) {
                                // 在重新调度 POLLING_INTERVAL 计时器之前，取消之前可能存在的 POLLING_INTERVAL 计时器
                                Long existingPollingIntervalTimerId = taskIdToPollingIntervalTimerId.get(taskId);
                                if (existingPollingIntervalTimerId != null) {
                                    timerWheel.cancelTimer(existingPollingIntervalTimerId);
                                    activeTimeouts.remove(existingPollingIntervalTimerId);
                                    log.debug(
                                        "[{}] Existing POLLING_INTERVAL timer {} cancelled for taskId: {} before "
                                            + "re-scheduling.",
                                        roleName(), existingPollingIntervalTimerId, taskId);
                                }
                                long newPollingIntervalTimerId = timerWheel.scheduleTimer(reEnqueueDeadlineMs);
                                // 存储原始的 TaskWrapper，以便在计时器到期时重新入队
                                activeTimeouts.put(newPollingIntervalTimerId,
                                    new DelayedTaskRequeue(taskId, task, taskWrapper, TimeoutType.POLLING_INTERVAL));
                                // 更新 taskIdToPollingIntervalTimerId 映射为新的延迟重新入队计时器 ID
                                taskIdToPollingIntervalTimerId.put(taskId, newPollingIntervalTimerId);
                                log.debug(
                                    "[{}] Scheduled delayed re-queue for taskId: {} with timerId: {} (interval {}ms)",
                                    roleName(), taskId, newPollingIntervalTimerId, currentPollingInterval.toMillis());
                            } else {
                                log.warn("[{}] Runner not active, not scheduling delayed re-queue for taskId: {}.", roleName(), taskId);
                            }
                        });
                        wakeup(); // 唤醒 polling 线程以处理新的调度命令
                    } else {
                        // 没有指定轮询间隔或间隔无效，立即重新入队
                        if (!shuttingDown.get() && running.get()) {
                            log.info("[{}] No polling interval, immediately re-queuing task {}.", roleName(), taskId);
                            taskQueue.offer(taskWrapper); // 重新入队包装器
                            wakeup(); // 唤醒以再次处理
                        } else {
                            log.warn("[{}] is shutting down, not re-polling task: {}.", roleName(), taskId);
                        }
                    }
                }
                workCount++;
            }
            return workCount;
        }

        @Override
        public void onStart() {
            log.info("[{}] started", roleName());
        }

        @Override
        public void onClose() {
            log.info("[{}] closed", roleName());
        }
    }
}
