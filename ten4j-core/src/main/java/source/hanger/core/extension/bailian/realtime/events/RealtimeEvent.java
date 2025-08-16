package source.hanger.core.extension.bailian.realtime.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 所有 Realtime API 事件的基抽象类。
 * 定义了事件类型字段，以便统一处理不同类型的事件。
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SessionCreatedEvent.class, name = "session.created"),
    @JsonSubTypes.Type(value = SessionUpdatedEvent.class, name = "session.updated"),
    @JsonSubTypes.Type(value = InputAudioBufferCommittedEvent.class, name = "input_audio_buffer.committed"),
    @JsonSubTypes.Type(value = InputAudioBufferSpeechStartedEvent.class, name = "input_audio_buffer.speech_started"),
    @JsonSubTypes.Type(value = InputAudioBufferSpeechStoppedEvent.class, name = "input_audio_buffer.speech_stopped"),
    @JsonSubTypes.Type(value = ItemCreatedEvent.class, name = "conversation.item.created"),
    @JsonSubTypes.Type(value = ResponseCreatedEvent.class, name = "response.created"),
    @JsonSubTypes.Type(value = ResponseOutputItemAddedEvent.class, name = "response.output_item.added"),
    @JsonSubTypes.Type(value = ResponseContentPartAddedEvent.class, name = "response.content_part.added"),
    @JsonSubTypes.Type(value = ResponseAudioTranscriptDeltaEvent.class, name = "response.audio_transcript.delta"),
    @JsonSubTypes.Type(value = ResponseAudioDeltaEvent.class, name = "response.audio.delta"),
    @JsonSubTypes.Type(value = ResponseAudioDoneEvent.class, name = "response.audio.done"),
    @JsonSubTypes.Type(value = ResponseAudioTranscriptDoneEvent.class, name = "response.audio_transcript.done"),
    @JsonSubTypes.Type(value = ResponseContentPartDoneEvent.class, name = "response.content_part.done"),
    @JsonSubTypes.Type(value = ResponseOutputItemDoneEvent.class, name = "response.output_item.done"),
    @JsonSubTypes.Type(value = ResponseDoneEvent.class, name = "response.done"),
    @JsonSubTypes.Type(value = ResponseTextDeltaEvent.class, name = "response.text.delta"),
    @JsonSubTypes.Type(value = ResponseTextDoneEvent.class, name = "response.text.done"),
    @JsonSubTypes.Type(value = FunctionCallArgumentsDoneEvent.class, name = "response.function_call_arguments.done"),
    @JsonSubTypes.Type(value = InputAudioTranscriptionCompletedEvent.class, name = "conversation.item.input_audio_transcription.completed"),
    // Internal events
    @JsonSubTypes.Type(value = ConnectionOpenedEvent.class, name = "connection.opened"),
    @JsonSubTypes.Type(value = ConnectionClosedEvent.class, name = "connection.closed"),
    @JsonSubTypes.Type(value = UnknownRealtimeEvent.class, name = "unknown")
})
@Getter
@Setter
@NoArgsConstructor
public abstract class RealtimeEvent {
    @JsonProperty("type")
    protected String type;
}
