package source.hanger.core.extension.dashscope.task;

public interface BailianPollingTask<T> {
    BailianPollingTaskRunner.PollingResult<T> execute() throws Throwable;
    void onComplete(T result);
    void onFailure(Throwable throwable);
    void onTimeout();
}
