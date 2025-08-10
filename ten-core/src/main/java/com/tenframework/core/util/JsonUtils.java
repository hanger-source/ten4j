package com.tenframework.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

/**
 * JsonUtils是一个辅助工具类，用于处理JSON相关的操作。
 */
@Slf4j
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
        // 私有构造函数，防止实例化
    }

    /**
     * 将点分路径转换为JSON Pointer路径。
     * 例如："properties.user.name" -> "/properties/user/name"
     * 
     * @param dotPath 点分路径字符串
     * @return JSON Pointer路径字符串
     */
    public static String convertDotPathToJsonPointer(String dotPath) {
        if (dotPath == null || dotPath.isEmpty()) {
            return "/"; // 根路径
        }
        // JSON Pointer中的特殊字符需要转义：/~ -> ~0, / -> ~1
        // 这里我们假设点路径不包含这些特殊字符，或者用户会确保其有效性。
        // 简单地将点替换为斜杠
        return "/" + dotPath.replace(".", "/");
    }

    /**
     * 根据点分路径从Map中获取值。
     *
     * @param sourceMap 来源Map。
     * @param dotPath   点分路径，例如 "a.b.c"。
     * @return 值的Optional，如果路径不存在或值为空则为Optional.empty()。
     */
    public static Optional<Object> getValueByPath(Map<String, Object> sourceMap, String dotPath) {
        if (sourceMap == null || dotPath == null || dotPath.isEmpty()) {
            return Optional.empty();
        }

        String[] pathSegments = dotPath.split("\\.");
        Object current = sourceMap;

        for (String segment : pathSegments) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(segment);
                if (current == null) {
                    return Optional.empty();
                }
            } else {
                return Optional.empty(); // 如果中间路径不是Map，则无法继续遍历
            }
        }
        return Optional.ofNullable(current);
    }

    /**
     * 根据点分路径向Map设置值。
     *
     * @param targetMap 目标Map。
     * @param dotPath   点分路径，例如 "a.b.c"。
     * @param value     要设置的值。
     */
    @SuppressWarnings("unchecked")
    public static void setValueByPath(Map<String, Object> targetMap, String dotPath, Object value) {
        if (targetMap == null || dotPath == null || dotPath.isEmpty()) {
            return;
        }

        String[] pathSegments = dotPath.split("\\.");
        Map<String, Object> currentMap = targetMap;

        for (int i = 0; i < pathSegments.length; i++) {
            String segment = pathSegments[i];
            if (i == pathSegments.length - 1) {
                // 最后一个片段，设置值
                currentMap.put(segment, value);
            } else {
                // 中间片段，获取或创建子Map
                Object next = currentMap.get(segment);
                if (!(next instanceof Map)) {
                    next = new ConcurrentHashMap<>(); // 使用 ConcurrentHashMap 以保证线程安全
                    currentMap.put(segment, next);
                }
                currentMap = (Map<String, Object>) next;
            }
        }
    }

    /**
     * 根据点分路径检查Map中是否存在值。
     *
     * @param sourceMap 来源Map。
     * @param dotPath   点分路径。
     * @return 如果路径存在且值非空则返回true，否则返回false。
     */
    public static boolean hasValueByPath(Map<String, Object> sourceMap, String dotPath) {
        return getValueByPath(sourceMap, dotPath).isPresent();
    }

    /**
     * 根据点分路径删除Map中的值。
     *
     * @param targetMap 目标Map。
     * @param dotPath   点分路径。
     */
    @SuppressWarnings("unchecked")
    public static void deleteValueByPath(Map<String, Object> targetMap, String dotPath) {
        if (targetMap == null || dotPath == null || dotPath.isEmpty()) {
            return;
        }

        String[] pathSegments = dotPath.split("\\.");
        Map<String, Object> currentMap = targetMap;

        for (int i = 0; i < pathSegments.length; i++) {
            String segment = pathSegments[i];
            if (i == pathSegments.length - 1) {
                // 最后一个片段，删除值
                currentMap.remove(segment);
            } else {
                Object next = currentMap.get(segment);
                if (next instanceof Map) {
                    currentMap = (Map<String, Object>) next;
                } else {
                    return; // 中间路径不是Map，无法继续删除
                }
            }
        }
    }

    /**
     * 尝试将Map转换为JsonNode。
     * 
     * @param map 待转换的Map
     * @return 转换后的JsonNode
     */
    public static JsonNode toJsonNode(Map<String, Object> map) {
        return OBJECT_MAPPER.valueToTree(map);
    }

    /**
     * 尝试将JsonNode转换为Map。
     * 
     * @param node 待转换的JsonNode
     * @return 转换后的Map
     */
    public static Map<String, Object> fromJsonNode(JsonNode node) {
        if (node instanceof ObjectNode) {
            return OBJECT_MAPPER.convertValue(node,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        }
        return Collections.emptyMap();
    }
}