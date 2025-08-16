package source.hanger.core.extension.bailian.realtime.events;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RealtimeEventType {
    // 会话初始化
    SESSION_UPDATE("session.update"),
    SESSION_CREATED("session.created"),
    SESSION_UPDATED("session.updated"),

    // 用户音频/图片输入
    INPUT_AUDIO_BUFFER_APPEND("input_audio_buffer.append"),
    INPUT_IMAGE_BUFFER_APPEND("input_image_buffer.append"),
    INPUT_AUDIO_BUFFER_SPEECH_STARTED("input_audio_buffer.speech_started"),
    INPUT_AUDIO_BUFFER_SPEECH_STOPPED("input_audio_buffer.speech_stopped"),
    INPUT_AUDIO_BUFFER_COMMITED("input_audio_buffer.committed"),
    // 注意：这里用的是"committed"，与您提供的列表一致，而不是"commited"

    // 服务器音频输出
    RESPONSE_CREATED("response.created"),
    RESPONSE_OUTPUT_ITEM_ADDED("response.output_item.added"),
    CONVERSATION_ITEM_CREATED("conversation.item.created"),
    RESPONSE_CONTENT_PART_ADDED("response.content_part.added"),
    RESPONSE_AUDIO_TRANSCRIPT_DELTA("response.audio_transcript.delta"),
    RESPONSE_AUDIO_DELTA("response.audio.delta"),
    RESPONSE_AUDIO_TRANSCRIPT_DONE("response.audio_transcript.done"),
    RESPONSE_AUDIO_DONE("response.audio.done"),
    RESPONSE_CONTENT_PART_DONE("response.content_part.done"),
    RESPONSE_OUTPUT_ITEM_DONE("response.output_item.done"),
    RESPONSE_DONE("response.done"),

    // 其他事件
    ERROR("error"),
    RESPONSE_TEXT_DELTA("response.text.delta"),
    RESPONSE_TEXT_DONE("response.text.done"),
    RESPONSE_FUNCTION_CALL_ARGUMENTS_DELTA("response.function_call_arguments.delta"),
    RESPONSE_FUNCTION_CALL_ARGUMENTS_DONE("response.function_call_arguments.done"),
    RESPONSE_TOOL_CALL_OUTPUT("response.tool_call.output"),
    ITEM_INPUT_AUDIO_TRANSCRIPTION_COMPLETED("conversation.item.input_audio_transcription.completed"),
    ITEM_INPUT_AUDIO_TRANSCRIPTION_FAILED("conversation.item.input_audio_transcription.failed"),

    // Connection events (internal to client/extension, not from API spec)
    CONNECTION_OPENED("connection.opened"),
    CONNECTION_CLOSED("connection.closed"),
    UNKNOWN("unknown");

    private final String value;

    RealtimeEventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    // Helper method to get enum from string value
    public static RealtimeEventType fromValue(String value) {
        for (RealtimeEventType type : RealtimeEventType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return UNKNOWN; // Or throw an IllegalArgumentException
    }
}
