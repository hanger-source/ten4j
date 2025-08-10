package source.hanger.core.app;

import source.hanger.core.connection.Connection;
import source.hanger.core.message.Message;

/**
 * `MessageReceiver` 接口定义了接收框架内消息的契约。
 * App 和 Engine 都可以实现此接口来处理传入的消息。
 */
public interface MessageReceiver {

    /**
     * 处理传入的消息。
     *
     * @param message    传入的消息。
     * @param connection 消息来源的连接，如果来自内部则为 null。
     */
    void handleInboundMessage(Message message, Connection connection);
}