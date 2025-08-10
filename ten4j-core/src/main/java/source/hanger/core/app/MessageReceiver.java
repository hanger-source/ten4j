package source.hanger.core.app;

import source.hanger.core.connection.Connection;
import source.hanger.core.message.Message;

/**
 * 消息接收器接口，定义了处理入站消息的方法。
 * 任何能够接收和处理消息的组件（如 App、Engine 或 Remote）都可以实现此接口。
 */
public interface MessageReceiver {
    /**
     * 处理一个入站消息。
     *
     * @param message    待处理的入站消息。
     * @param connection 消息的来源连接，如果来自内部则为 null。
     * @return 如果消息被成功处理并接受，则返回 true；否则返回 false。
     */
    boolean handleInboundMessage(Message message, Connection connection);
}