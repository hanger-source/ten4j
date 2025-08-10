package source.hanger.core.connection;

/**
 * 表示连接的依附目标类型，对齐 C 语言的 TEN_CONNECTION_ATTACH_TO。
 */
public enum ConnectionAttachTo {
    /**
     * 无效或未依附状态。
     */
    INVALID,

    /**
     * 连接依附于 App。
     */
    APP,

    /**
     * 连接依附于 Engine。这通常是 App 和 Engine 在不同线程时，连接迁移的中间状态。
     */
    ENGINE,

    /**
     * 连接依附于 Remote (在 C 语言中被视为 REMOTE)。
     */
    REMOTE
}