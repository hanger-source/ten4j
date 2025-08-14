package source.hanger.core.extension.bailian.realtime.events;

import com.google.gson.JsonObject;

/**
 * 所有 Realtime API 事件的基接口。
 * 定义了获取事件类型的方法，以便统一处理不同类型的事件。
 */
public interface RealtimeEvent {
    /**
     * 获取事件类型字符串。
     * 例如: "session.created", "response.text_delta",
     * "function_call_arguments_done"等。
     * 
     * @return 事件类型字符串。
     */
    String getType();

    /**
     * 将事件对象转换为 JsonObject。
     * 
     * @return 对应的 JsonObject 表示。
     */
    JsonObject toJsonObject();
}
