package source.hanger.core.protocol;

import source.hanger.core.message.Message;

/**
 * Protocol 接口定义了数据协议层应具备的能力。
 * 在当前的 Java 实现中，例如 {@link source.hanger.server.connection.NettyConnection}，
 * 消息的编解码职责已通过 {@link com.fasterxml.jackson.databind.ObjectMapper}
 * 直接集成到 Connection 的实现内部，并利用 Netty 的 {@code ChannelPipeline} 机制处理底层协议。
 * 因此，本接口目前并未被具体 Connection 实现类直接引用和使用，但保留其定义以保持与 C/Python 底层设计的概念对齐。
 * 未来如果需要支持更灵活的多种协议，或进一步解耦编解码逻辑，可以考虑实现此接口。
 */
public interface Protocol {

    // 编码消息，将内部 Message 对象转换为字节数组或特定协议格式
    byte[] encode(Message message);

    // 解码数据，将字节数组或特定协议格式转换为内部 Message 对象
    Message decode(byte[] data);

    // 协议握手或初始化操作
    void handshake();

    // 清理协议相关的资源
    void cleanup();
}