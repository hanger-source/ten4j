package source.hanger.core.extension.system;

public final class ExtensionConstants {

    public static final String CMD_CHAT_COMPLETION_CALL = "chat_completion_call";

    public static final String LLM_DATA_OUT_NAME = "text_data";
    public static final String DATA_OUT_PROPERTY_TEXT = "text";
    public static final String DATA_OUT_PROPERTY_END_OF_SEGMENT = "end_of_segment";

    // TTS 相关的常量
    public static final String DATA_TRANSCRIPT_NAME = "text_data";
    public static final String DATA_IN_PROPERTY_QUIET = "quiet";

    public static final String CMD_IN_FLUSH = "flush";
    public static final String CMD_OUT_FLUSH = "flush";

    public static final String CMD_IN_ON_USER_JOINED = "in_on_user_joined";
    public static final String CMD_IN_ON_USER_LEFT = "in_on_user_left";

    private ExtensionConstants() {
        // Prevent instantiation
    }
}