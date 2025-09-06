package source.hanger.core.common;

public final class ExtensionConstants {

    public static final String CMD_CHAT_COMPLETION_CALL = "chat_completion_call";

    public static final String TEXT_DATA_OUT_NAME = "text_data";
    public static final String ASR_DATA_OUT_NAME = "asr_result";
    public static final String DATA_OUT_PROPERTY_TEXT = "text";
    public static final String DATA_OUT_PROPERTY_ROLE = "role";
    public static final String DATA_OUT_PROPERTY_END_OF_SEGMENT = "end_of_segment";
    public static final String DATA_OUT_PROPERTY_IS_FINAL = "is_final";

    // TTS 相关的常量
    public static final String DATA_IN_PROPERTY_QUIET = "quiet";

    public static final String CMD_IN_FLUSH = "flush";
    public static final String CMD_OUT_FLUSH = "flush";

    public static final String CMD_IN_ON_USER_JOINED = "on_user_joined";
    public static final String CMD_IN_ON_USER_LEFT = "on_user_left";

    // Tooling related constants
    public static final String CMD_TOOL_REGISTER = "tool_register";// 工具注册命令
    public static final String CMD_PROPERTY_TOOL = "tool"; // 工具属性键
    public static final String CMD_TOOL_CALL = "tool_call";
    public static final String CMD_TOOL_CALL_PROPERTY_NAME = "name";
    public static final String CMD_TOOL_CALL_PROPERTY_ARGUMENTS = "arguments";
    public static final String CMD_TOOL_CALL_PROPERTY_TOOL_CALL_ID = "tool_call_id";
    public static final String CMD_PROPERTY_RESULT = "result";

    public static final String CONTENT_DATA_OUT_NAME = "content_data";
    // ten-framework不存在这种类型，自创的委托，特定情况（例如llm节点直接委托给其他llm节点）会将 text 委托给其他extension处理
    public static final String DELEGATE_TEXT_DATA_OUT_NAME = "delegate_text_data";

    public static final String DEST_TTS_EXTENSION_PROPERTY_NAME = "dest_tts";

    private ExtensionConstants() {
        // Prevent instantiation
    }
}