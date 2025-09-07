package source.hanger.core.extension.component.tool;

import source.hanger.core.extension.base.tool.ToolCallPayload.ToolCallPayloadBuilder;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public interface ToolCallPayloadEmitter {
    /**
     * 发送一个进度或中间状态的更新。
     */
    void emmit(ToolCallPayloadBuilder<?, ?> payload);
}
