package source.hanger.core.extension.component.state;

import lombok.extern.slf4j.Slf4j;

/**
 * Extension 状态提供者的默认实现类。
 * 统一管理 Extension 的运行状态和中断状态。
 *
 * 不需要Atomic 多线程，实际上正常情况下 Extension只有一个runloop的线程来修改状态。
 */
@Slf4j
public class DefaultExtensionStateProvider implements ExtensionStateProvider {

    private final String extensionName;
    private volatile boolean running = false;
    private volatile boolean interrupted = false;

    public DefaultExtensionStateProvider(String extensionName) {
        this.extensionName = extensionName != null ? extensionName : "UnknownExtension";
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    public boolean isInterrupted() {
        return interrupted;
    }

    @Override
    public void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }

    @Override
    public void start() {
        log.info("[{}] Extension 状态提供者: 启动", extensionName);
        setRunning(true);
        setInterrupted(false);
    }

    @Override
    public void stop() {
        log.info("[{}] Extension 状态提供者: 停止", extensionName);
        setInterrupted(true);
        setRunning(false);
    }

    @Override
    public void reset() {
        log.info("[{}] Extension 状态提供者: 重置", extensionName);
        setRunning(false);
        setInterrupted(false);
    }

    @Override
    public String toString() {
        return String.format("ExtensionState[%s: running=%s, interrupted=%s, active=%s]", 
            extensionName, isRunning(), isInterrupted(), isActive());
    }
}
