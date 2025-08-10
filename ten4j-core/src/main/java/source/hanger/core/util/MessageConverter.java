package source.hanger.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import source.hanger.core.graph.MessageConversionContext;
import source.hanger.core.message.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.HashMap;

/**
 * 负责在 Ten 框架中执行消息转换的工具类。
 * 能够根据 `MessageConversionContext` 中定义的规则转换 `Message` 对象。
 *
 * 这对齐了 C 端 `msg_conversion` 的概念。
 */
@Slf4j
public class MessageConverter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MessageConverter() {
        // Utility class
    }

    /**
     * 根据提供的转换上下文，尝试转换消息。
     *
     * @param originalMessage 原始消息。
     * @param context         消息转换上下文。
     * @return 转换后的消息，如果无法转换或不满足条件则返回原始消息。
     */
    public static Message convertMessage(Message originalMessage, MessageConversionContext context) {
        if (originalMessage == null || context == null) {
            return originalMessage;
        }

        // 检查转换条件（如果存在）
        if (context.getCondition() != null && !checkCondition(originalMessage, context.getCondition())) {
            log.debug("MessageConverter: 消息 {} 不满足转换条件，跳过转换。", originalMessage.getId());
            return originalMessage;
        }

        // 检查消息名称是否匹配源消息名称
        if (context.getSrcMsgName() != null && !context.getSrcMsgName().equals(originalMessage.getName())) {
            log.debug("MessageConverter: 消息名称 {} 与源消息名称 {} 不匹配，跳过转换。",
                    originalMessage.getName(), context.getSrcMsgName());
            return originalMessage;
        }

        // 根据转换类型执行转换
        switch (context.getConversionType()) {
            case "json_property_remap":
                return remapJsonProperties(originalMessage, context.getRules());
            // TODO: 添加其他转换类型，例如 "type_conversion", "default_value" 等
            default:
                log.warn("MessageConverter: 未知或不支持的消息转换类型: {}", context.getConversionType());
                return originalMessage;
        }
    }

    /**
     * 检查消息是否满足转换条件。
     * 简单的实现：检查 properties 中是否存在指定的键值对。
     * 实际可能需要更复杂的表达式解析。
     */
    private static boolean checkCondition(Message message, Map<String, Object> condition) {
        if (message.getProperties() == null || condition.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            if (!message.getProperties().containsKey(entry.getKey()) ||
                    !message.getProperties().get(entry.getKey()).equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行 JSON 属性的重映射转换。
     * 假设 rules 是一个 Map，其中 key 是原始路径，value 是目标路径。
     * 例如：{"old_path": "new_path"}
     * 或者更复杂的规则，例如 {"src_field": "dst_field", "type_cast": "int"}
     */
    private static Message remapJsonProperties(Message originalMessage, Map<String, Object> rules) {
        if (originalMessage.getProperties() == null || rules == null || rules.isEmpty()) {
            return originalMessage;
        }

        Message convertedMessage = null;
        try {
            convertedMessage = originalMessage.clone(); // 克隆消息以避免修改原始消息
        } catch (CloneNotSupportedException e) {
            log.error("MessageConverter: 消息克隆失败: {}", e.getMessage());
            return originalMessage; // 克隆失败，返回原始消息
        }

        Map<String, Object> newProperties = new HashMap<>();
        for (Map.Entry<String, Object> entry : originalMessage.getProperties().entrySet()) {
            String originalKey = entry.getKey();
            Object originalValue = entry.getValue();

            // 检查规则中是否有针对此原始键的重映射
            if (rules.containsKey(originalKey) && rules.get(originalKey) instanceof String) {
                String newKey = (String) rules.get(originalKey);
                newProperties.put(newKey, originalValue);
                log.debug("MessageConverter: 重映射属性: {} -> {}", originalKey, newKey);
            } else {
                newProperties.put(originalKey, originalValue); // 如果没有重映射规则，则保持不变
            }
        }
        convertedMessage.setProperties(newProperties); // 更新转换后的消息的属性

        // TODO: 如果有 dst_msg_name，可能需要修改消息的 name 字段
        // TODO: 如果有 dst_msg_type，可能需要修改消息的 type 字段 (但需要考虑 MessageType 枚举)

        return convertedMessage;
    }

    // TODO: 可以添加更多辅助方法来处理复杂属性路径 (例如 "field.subfield")
    // TODO: 可以添加类型转换逻辑 (例如从 String 到 Int)
}