package source.hanger.core.extension.system;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * 简单的聊天记忆管理类，用于存储和管理对话历史。
 * 支持根据最大历史长度进行截断。
 */
@Slf4j
public class ChatMemory {

    private final List<Map<String, Object>> history;
    private final int maxHistory;
    private Consumer<Map<String, Object>> onMemoryExpiredCallback; // Callback for expired messages
    private Consumer<Map<String, Object>> onMemoryAppendedCallback; // Callback for appended messages

    public ChatMemory(int maxHistory) {
        this.maxHistory = maxHistory;
        this.history = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * 添加消息到历史记录并智能截断。
     *
     * @param role    消息角色 (e.g., "user", "assistant")
     * @param content 消息内容
     */
    public void put(String role, String content) {
        Map<String, Object> msg = new LinkedHashMap<>(); // Use LinkedHashMap to preserve insertion order
        msg.put("role", role);
        msg.put("content", content);
        put(msg);
    }

    /**
     * 添加消息（Map形式）到历史记录并智能截断。
     *
     * @param message 消息的 Map 表示
     */
    public void put(Map<String, Object> message) {
        history.add(message);
        smartTruncateHistory();
        if (onMemoryAppendedCallback != null) {
            onMemoryAppendedCallback.accept(message);
        }
    }

    /**
     * 智能截断历史记录，保持不超过 maxHistory 条。
     */
    private void smartTruncateHistory() {
        while (history.size() > maxHistory) {
            Map<String, Object> removed = history.remove(0); // 移除最旧的消息
            if (onMemoryExpiredCallback != null) {
                onMemoryExpiredCallback.accept(removed);
            }
            log.debug("Chat memory truncated, removed: {}", removed);
        }
    }

    /**
     * 获取当前的聊天历史记录。
     *
     * @return 聊天历史记录的不可修改视图
     */
    public List<Map<String, Object>> get() {
        return Collections.unmodifiableList(history);
    }

    /**
     * 清空聊天历史记录。
     */
    public void clear() {
        history.clear();
        log.info("Chat memory cleared.");
    }

    /**
     * 注册内存过期回调。
     *
     * @param callback 回调函数，接收过期消息的 Map 表示
     */
    public void onMemoryExpired(Consumer<Map<String, Object>> callback) {
        this.onMemoryExpiredCallback = callback;
    }

    /**
     * 注册内存追加回调。
     *
     * @param callback 回调函数，接收新追加消息的 Map 表示
     */
    public void onMemoryAppended(Consumer<Map<String, Object>> callback) {
        this.onMemoryAppendedCallback = callback;
    }

    /**
     * 获取当前历史记录中的消息数量。
     *
     * @return 消息数量
     */
    public int size() {
        return history.size();
    }
}
