package source.hanger.core.extension.component.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.state.ExtensionStateProvider;

/**
 * Extension 状态提供者的默认实现类。
 * 统一管理 Extension 的运行状态和中断状态。
 */
@Slf4j
public class DefaultExtensionStateProvider implements ExtensionStateProvider {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    private final String extensionName;

    public DefaultExtensionStateProvider(String extensionName) {
        this.extensionName = extensionName != null ? extensionName : "UnknownExtension";
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void setRunning(boolean running) {
        boolean previous = this.running.getAndSet(running);
        if (previous != running) {
            log.debug("[{}] Extension 运行状态变更: {} -> {}", extensionName, previous, running);
        }
    }

    @Override
    public boolean isInterrupted() {
        return interrupted.get();
    }

    @Override
    public void setInterrupted(boolean value) {
        boolean previous = this.interrupted.getAndSet(value);
        if (previous != value) {
            log.debug("[{}] Extension 中断状态变更: {} -> {}", extensionName, previous, value);
        }
    }

    @Override
    public AtomicBoolean getInterruptedFlag() {
        return interrupted;
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
