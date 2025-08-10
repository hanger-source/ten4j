package com.tenframework.core.remote;

import com.tenframework.core.engine.Engine;
import com.tenframework.core.message.Location;
import com.tenframework.core.message.Message;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 远程 Engine 实例的抽象。
 * 负责管理与远程 Engine 的逻辑连接，并将本地 Engine 产生的发往远程的消息转发出去。
 * 对齐C/Python中的ten_remote_t结构体。
 * 不包含具体的网络传输细节（如 Netty）。
 */
@Data
@Slf4j
public abstract class Remote {

    // 远程 Engine 的唯一标识符，通常是其 appUri
    private final String remoteId; // 对应 ten_remote_t 的 remote_id，或远程 appUri

    // 远程 Engine 的 Location，包含 appUri, graphId 等信息
    private final Location remoteEngineLocation;

    // 本地 Engine 的引用，用于接收远程返回的消息
    private final Engine localEngine;

    // 标识 Remote 是否活跃（例如，是否有底层连接）
    private volatile boolean active;

    public Remote(String remoteId, Location remoteEngineLocation, Engine localEngine) {
        this.remoteId = remoteId;
        this.remoteEngineLocation = remoteEngineLocation;
        this.localEngine = localEngine;
        this.active = false; // 初始为不活跃
        log.info("Remote {}: 创建，目标Engine Location: {}", remoteId, remoteEngineLocation.toString());
    }

    /**
     * 激活 Remote，建立底层连接。
     * 这将是一个抽象方法，由具体实现（如 WebSocketRemote）负责建立实际连接。
     */
    public abstract void activate();

    /**
     * 将消息发送到远程 Engine。
     * 这将是一个抽象方法，由具体实现负责消息的序列化和网络传输。
     *
     * @param message 要发送的消息
     */
    public abstract void sendMessage(Message message);

    /**
     * 关闭此 Remote 及其所有底层资源。
     * 这将是一个抽象方法，由具体实现负责关闭实际连接和释放资源。
     */
    public abstract void shutdown();

    public boolean isActive() {
        return active;
    }

    protected void setActive(boolean active) { // 允许子类设置活跃状态
        this.active = active;
    }
}