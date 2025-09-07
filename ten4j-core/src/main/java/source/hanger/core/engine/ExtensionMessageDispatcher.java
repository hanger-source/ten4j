package source.hanger.core.engine;

import source.hanger.core.message.Message;

/**
 * 负责将消息分发到Engine内部的Extension。
 * 解耦Engine的核心消息循环与Extension的具体调用逻辑。
 * 现在它主要作为 Engine 与 ExtensionContext 之间消息分发的桥梁。
 */
public interface ExtensionMessageDispatcher {

    /**
     * 分发通用消息到对应的Extension。
     *
     * @param message 待分发的消息
     */
    void dispatchMessage(Message message);

}