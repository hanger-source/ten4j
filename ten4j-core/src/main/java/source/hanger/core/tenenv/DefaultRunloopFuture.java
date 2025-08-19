package source.hanger.core.tenenv;

import source.hanger.core.runloop.Runloop;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Default implementation of {@link RunloopFuture} that wraps a {@link CompletableFuture}
 * and ensures all callbacks are executed on a specified {@link Runloop} thread.
 *
 * @param <T> The type of the result.
 */
public class DefaultRunloopFuture<T> implements RunloopFuture<T> {

    private final CompletableFuture<T> delegate;
    private final Runloop runloop;

    // 私有构造函数
    private DefaultRunloopFuture(CompletableFuture<T> delegate, Runloop runloop) {
        this.delegate = delegate;
        this.runloop = runloop;
    }

    // 静态工厂方法：包装一个已有的 CompletableFuture
    public static <T> RunloopFuture<T> wrapCompletableFuture(CompletableFuture<T> future, Runloop runloop) {
        return new DefaultRunloopFuture<>(future, runloop);
    }

    // 静态工厂方法：创建一个已成功完成的 RunloopFuture
    public static <T> RunloopFuture<T> completedFuture(T value, Runloop runloop) {
        return new DefaultRunloopFuture<>(CompletableFuture.completedFuture(value), runloop);
    }

    // 静态工厂方法：创建一个已异常完成的 RunloopFuture
    public static <T> RunloopFuture<T> failedFuture(Throwable ex, Runloop runloop) {
        return new DefaultRunloopFuture<>(CompletableFuture.failedFuture(ex), runloop);
    }

    /**
     * 异步执行一个提供 RunloopFuture 的 Supplier，并在指定的 Runloop 线程上完成结果。
     *
     * @param supplier 提供 RunloopFuture 的 Supplier。
     * @param runloop  要在其上执行 Supplier 和完成结果的 Runloop。
     * @param <T>      结果类型。
     * @return 一个新的 RunloopFuture，其结果将由 Supplier 提供并在 Runloop 线程上完成。
     */
    public static <T> RunloopFuture<T> supplyRunloopAsync(Supplier<RunloopFuture<T>> supplier, Runloop runloop) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runloop.postTask(() -> {
            try {
                supplier.get().whenComplete((result, ex) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                    } else {
                        future.complete(result);
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return new DefaultRunloopFuture<>(future, runloop);
    }

    // Helper to run actions on the Runloop thread
    private <U> RunloopFuture<U> wrap(CompletableFuture<U> future) {
        return new DefaultRunloopFuture<>(future, runloop);
    }

    private Executor getRunloopExecutor() {
        return runloop::postTask;
    }

    // --- Core CompletionStage methods, wrapped to ensure Runloop execution ---;

    @Override
    public RunloopFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return wrap(delegate.whenCompleteAsync(action, getRunloopExecutor()));
    }

    @Override
    public <U> RunloopFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return wrap(delegate.thenApplyAsync(fn, getRunloopExecutor()));
    }

    @Override
    public RunloopFuture<Void> thenAccept(Consumer<? super T> action) {
        return wrap(delegate.thenAcceptAsync(action, getRunloopExecutor()));
    }

    @Override
    public RunloopFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        // 使用 handleAsync 来确保回调在 Runloop 线程上执行
        return wrap(delegate.handleAsync((result, ex) -> {
            if (ex != null) {
                return fn.apply(ex);
            }
            return result;
        }, getRunloopExecutor()));
    }

    // --- Additional CompletionStage methods, ensuring Runloop execution ---

    @Override
    public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return runAfterBothAsync(other, action, getRunloopExecutor());
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterBothAsync(other, action, getRunloopExecutor())); // Ensure async on Runloop
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return wrap(delegate.runAfterBothAsync(other, action, executor));
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return thenAcceptBothAsync(other, action, getRunloopExecutor());
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return wrap(delegate.thenAcceptBothAsync(other, action, getRunloopExecutor())); // Ensure async on Runloop
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
        return wrap(delegate.thenAcceptBothAsync(other, action, executor));
    }

    @Override
    public <U, V> CompletionStage<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return thenCombineAsync(other, fn, getRunloopExecutor());
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return wrap(delegate.thenCombineAsync(other, fn, getRunloopExecutor())); // Ensure async on Runloop
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return wrap(delegate.thenCombineAsync(other, fn, executor));
    }

    @Override
    public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return runAfterEitherAsync(other, action, getRunloopExecutor());
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return wrap(delegate.runAfterEitherAsync(other, action, getRunloopExecutor())); // Ensure async on Runloop
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return wrap(delegate.runAfterEitherAsync(other, action, executor));
    }

    @Override
    public <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return applyToEitherAsync(other, fn, getRunloopExecutor());
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return wrap(delegate.applyToEitherAsync(other, fn, getRunloopExecutor())); // Ensure async on Runloop
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
        return wrap(delegate.applyToEitherAsync(other, fn, executor));
    }

    @Override
    public CompletionStage<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return acceptEitherAsync(other, action, getRunloopExecutor());
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return wrap(delegate.acceptEitherAsync(other, action, getRunloopExecutor())); // Ensure async on Runloop
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
        return wrap(delegate.acceptEitherAsync(other, action, executor));
    }

    @Override
    public CompletionStage<Void> thenRun(Runnable action) {
        return thenRunAsync(action, getRunloopExecutor());
    }

    @Override
    public CompletionStage<Void> thenRunAsync(Runnable action) {
        return wrap(delegate.thenRunAsync(action, getRunloopExecutor())); // Ensure async on Runloop
    }

    @Override
    public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
        return wrap(delegate.thenRunAsync(action, executor));
    }

    // --- Methods to satisfy CompletionStage interface, passing through to delegate ---;
    @Override
    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return whenComplete(action); // Delegates to the whenComplete method which uses getRunloopExecutor()
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return wrap(delegate.whenCompleteAsync(action, executor));
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return thenApply(fn); // Delegates to the thenApply method which uses getRunloopExecutor()
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return wrap(delegate.thenApplyAsync(fn, executor));
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action) {
        return thenAccept(action); // Delegates to the thenAccept method which uses getRunloopExecutor()
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return wrap(delegate.thenAcceptAsync(action, executor));
    }

    @Override
    public <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return wrap(delegate.thenComposeAsync(fn, getRunloopExecutor())); // Ensure async on Runloop
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenCompose(fn); // Delegates to the thenCompose method which uses getRunloopExecutor()
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return wrap(delegate.thenComposeAsync(fn, executor));
    }

    @Override
    public <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return handleAsync(fn, getRunloopExecutor());
    }

    @Override
    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return wrap(delegate.handleAsync(fn, getRunloopExecutor())); // Ensure async on Runloop
    }

    @Override
    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return wrap(delegate.handleAsync(fn, executor));
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return delegate;
    }
}
