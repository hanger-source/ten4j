package com.tenframework.core.engine;

/**
 * Engine生命周期状态枚举
 * 对应C语言中的Engine状态管理
 */
public enum EngineState {
    /**
     * 初始化状态，Engine已创建但未启动
     */
    CREATED,

    /**
     * 正在启动中
     */
    STARTING,

    /**
     * 运行状态，可以处理消息
     */
    RUNNING,

    /**
     * 正在停止中
     */
    STOPPING,

    /**
     * 已停止状态
     */
    STOPPED,

    /**
     * 错误状态
     */
    ERROR
}