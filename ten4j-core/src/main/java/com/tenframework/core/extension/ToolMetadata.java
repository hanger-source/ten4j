package com.tenframework.core.extension;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工具元数据接口
 * 用于描述Extension的工具能力，对应LLMToolMetadata
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolMetadata {

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 工具版本
     */
    private String version;

    /**
     * 工具类型
     */
    private String type;

    /**
     * 工具参数定义
     */
    private List<ToolParameter> parameters;

    /**
     * 工具属性
     */
    private Map<String, Object> properties;

    /**
     * 工具参数定义
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ToolParameter {
        /**
         * 参数名称
         */
        private String name;

        /**
         * 参数类型
         */
        private String type;

        /**
         * 参数描述
         */
        private String description;

        /**
         * 是否必需
         */
        private boolean required;

        /**
         * 默认值
         */
        private Object defaultValue;

        /**
         * 参数约束
         */
        private Map<String, Object> constraints;
    }
} 