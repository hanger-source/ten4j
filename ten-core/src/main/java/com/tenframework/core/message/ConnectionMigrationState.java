package com.tenframework.core.message;

public enum ConnectionMigrationState {
    INITIAL, // 初始状态，Connection 由 App 维护
    FIRST_MSG, // 收到第一个消息，待迁移到 Engine
    MIGRATING, // 迁移中
    MIGRATED, // 迁移完成，Connection 由 Engine 维护并绑定
    CLOSED, // 连接已关闭 (新增)
    CLEANING, // 清理中
    CLEANED // 清理完成
}