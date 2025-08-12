package source.hanger.core.extension.system.llm;

public final class LlmConstants {

    public static final String CMD_CHAT_COMPLETION_CALL = "chat_completion_call";

    public static final String DATA_OUT_NAME = "text_data";
    public static final String DATA_OUT_PROPERTY_TEXT = "text";
    public static final String DATA_OUT_PROPERTY_END_OF_SEGMENT = "end_of_segment";

    public static final String CMD_IN_FLUSH = "in_flush";
    public static final String CMD_IN_ON_USER_JOINED = "in_on_user_joined";
    public static final String CMD_IN_ON_USER_LEFT = "in_on_user_left";

    private LlmConstants() {
        // Prevent instantiation
    }
}