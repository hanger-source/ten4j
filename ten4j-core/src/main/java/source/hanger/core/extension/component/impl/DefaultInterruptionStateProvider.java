package source.hanger.core.extension.component.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import source.hanger.core.extension.component.flush.InterruptionStateProvider;

/**
 * 中断状态提供者接口的实现类。
 * 负责拥有、设置和查询全局中断标志。
 */
public class DefaultInterruptionStateProvider implements InterruptionStateProvider {

    private final AtomicBoolean interrupted = new AtomicBoolean(false); // 内部拥有此中断标志

    @Override
    public boolean isInterrupted() {
        return interrupted.get();
    }

    @Override
    public void setInterrupted(boolean value) {
        interrupted.set(value);
    }

    @Override
    public AtomicBoolean getInterruptedFlag() {
        return interrupted;
    }
}
