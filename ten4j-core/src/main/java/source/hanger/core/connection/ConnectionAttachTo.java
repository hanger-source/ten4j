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
     * // When the app and the engine run in different threads, and the connection
     * // migration should be completed before the engine handles the messages from
     * // the connection. Once the migration is completed, the messages will be
     * // pushed to the queue of the engine, in other words, the connection can be
     * // seen as attaching to the engine before attaching to a remote formally
     * // (because that remote has not been created yet). So we use
     * // 'ATTACH_TO_ENGINE' as the intermediate state to ensure that getting the
     * // correct eventloop based on the 'ten_connection_t::attach_to_type' field.
     * //
     * // Note that the 'ten_connection_t::attach_to_type' will be 'ENGINE' or
     * // 'REMOTE' when the 'ten_connection_t::migration_state' is 'DONE'. In other
     * // words, the connection will use the engine's eventloop once the migration is
     * // done.
     */
    ENGINE,

    /**
     * 连接依附于 Remote (在 C 语言中被视为 REMOTE)。
     */
    REMOTE
}