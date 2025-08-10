package source.hanger.core.engine;

import source.hanger.core.message.Message;

/**
 * 消息提交器接口，定义了向Engine提交消息的方法。
 * 用于解耦EngineTenEnv和Engine的直接依赖。
 */
public interface MessageSubmitter {

    /**
     * 向Engine提交消息（非阻塞）
     *
     * @param message 要处理的消息
     * @return true如果成功提交，false如果队列已满
     */
    boolean submitMessage(Message message);
}