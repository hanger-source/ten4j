package source.hanger.core.util;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

/**
 * JsonUtils是一个辅助工具类，用于处理JSON相关的操作。
 */
@Slf4j
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
        // 私有构造函数，防止实例化
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public static String writeValueAsString(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Error writing value as string: {}", e.getMessage(), e);
            return null;
        }
    }

    public static <T> T readValue(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Error reading value: {}", e.getMessage(), e);
            return null;
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