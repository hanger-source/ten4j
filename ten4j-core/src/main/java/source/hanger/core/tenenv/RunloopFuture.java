package source.hanger.core.tenenv;

import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Consumer;

/**
 * Represent a future that ensures its callbacks are executed on the associated Runloop thread.
 * This interface mimics key methods of CompletionStage/CompletableFuture,
 * but with an implicit guarantee of Runloop execution for all chained operations.
 *
 * @param <T> The type of the result.
 */
public interface RunloopFuture<T> extends CompletionStage<T> {

    /**
     * Returns a new RunloopFuture that, when this future completes, is completed by
     * the given action executed on the Runloop thread.
     *
     * @param action the action to perform
     * @return the new RunloopFuture
     */
    @Override
    RunloopFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

    /**
     * Returns a new RunloopFuture that, when this future completes normally, is
     * completed with the result of the given function executed on the Runloop thread.
     *
     * @param fn the function to use to compute the value of the new RunloopFuture
     * @param <U> the function's return type
     * @return the new RunloopFuture
     */
    @Override
    <U> RunloopFuture<U> thenApply(Function<? super T, ? extends U> fn);

    /**
     * Returns a new RunloopFuture that, when this future completes normally, is
     * executed with the given action on the Runloop thread.
     *
     * @param action the action to perform
     * @return the new RunloopFuture
     */
    @Override
    RunloopFuture<Void> thenAccept(Consumer<? super T> action);


    /**
     * Returns a new RunloopFuture that, when this future completes exceptionally, is
     * completed with the result of the given function applied to the exception executed on the Runloop thread.
     *
     * @param fn the function to use to compute the value of the new RunloopFuture
     * @return the new RunloopFuture
     */
    @Override
    RunloopFuture<T> exceptionally(Function<Throwable, ? extends T> fn);
}
