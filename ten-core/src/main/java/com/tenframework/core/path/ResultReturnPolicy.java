package com.tenframework.core.path;

/**
 * 命令结果返回策略枚举
 * 对应C语言中的TEN_RESULT_RETURN_POLICY
 */
public enum ResultReturnPolicy {
    /**
     * 优先返回第一个错误，或等待所有OK结果并返回最后一个OK结果
     * (FIRST_ERROR_OR_LAST_OK)
     */
    FIRST_ERROR_OR_LAST_OK,

    /**
     * 返回每个OK或ERROR结果（流式结果）
     * (EACH_OK_AND_ERROR)
     */
    EACH_OK_AND_ERROR
}