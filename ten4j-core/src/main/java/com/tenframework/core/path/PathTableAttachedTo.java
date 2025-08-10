package com.tenframework.core.path;

/**
 * `PathTableAttachedTo` 表示 `PathTable` 实例所依附的目标类型。
 * 对应 C 语言中的 `TEN_PATH_TABLE_ATTACH_TO` 枚举。
 */
public enum PathTableAttachedTo {
    INVALID,
    APP,
    ENGINE,
    EXTENSION
}