package source.hanger.core.extension.component.context;

import java.util.List;

/**
 * LLM 上下文管理器接口。
 * 负责管理 LLM 对话历史和系统提示。
 *
 * @param <MESSAGE> LLM 对话消息的类型。
 */
public interface LLMContextManager<MESSAGE> {

    /**
     * 获取用于发送给 LLM 的完整消息列表，包括系统提示和对话历史。
     *
     * @return 包含所有上下文消息的列表。
     */
    List<MESSAGE> getMessagesForLLM();

    /**
     * 添加一个新的用户或助手消息到对话历史。
     *
     * @param content 消息内容。
     */
    void onAssistantMsg(String content);

    void onAssistantMsg(MESSAGE message);

    void onUserMsg(String content);

    void onUserVideoMsg(String content, List<String> base64Images);
    /**
     * 添加一个 LLM 特定消息对象到对话历史。
     * 例如，用于添加工具调用消息或工具结果消息。
     *
     * @param message LLM 特定消息对象。
     */
    void onToolCallMsg(MESSAGE message);
    /**
     * 清空对话历史。
     */
    void clearHistory();

    /**
     * 设置对话历史的最大长度。
     *
     * @param maxHistory 最大历史消息数量。
     */
    void setMaxHistory(int maxHistory);

    /**
     * LLMContextManager 的生命周期结束回调。
     * 用于执行清理操作。
     */
    void onDestroy();
}
