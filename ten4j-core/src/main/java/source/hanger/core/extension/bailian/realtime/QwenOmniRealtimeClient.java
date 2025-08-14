package source.hanger.core.extension.bailian.realtime;

import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonObject;
import io.reactivex.Flowable;
import io.reactivex.processors.PublishProcessor;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import io.reactivex.disposables.Disposable;

/**
 * 封装与 DashScope Omni Realtime API 的低级交互。
 * 处理 WebSocket 连接、发送数据和接收原始 JSON 事件。
 */
@Slf4j
public class QwenOmniRealtimeClient {

    private OmniRealtimeConversation conversation;
    // Removed apiKey, model, sampleRate as they will be part of OmniRealtimeParam
    private final OmniRealtimeParam param; // Store the parameter for reconnection
    private final PublishProcessor<source.hanger.core.extension.bailian.realtime.events.RealtimeEvent> eventProcessor; // To
                                                                                                                       // push
                                                                                                                       // events
                                                                                                                       // received
                                                                                                                       // from
                                                                                                                       // OmniRealtimeCallback
    private ScheduledExecutorService executorService;
    private Disposable disposable;
    private boolean isManualDisconnect = false;

    // Reconnection parameters
    private static final int RECONNECT_DELAY_SECONDS = 5; // Reconnect after 5 seconds

    public QwenOmniRealtimeClient(OmniRealtimeParam param) {
        this.param = param;
        this.eventProcessor = PublishProcessor.create();
        this.executorService = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * 连接到 Realtime API。
     *
     * @throws NoApiKeyException 如果 API 密钥未设置或无效
     * @throws Exception         如果连接失败
     */
    public void connect() throws NoApiKeyException, Exception {
        log.info("[qwen_omni_realtime_client] Attempting to connect to Realtime API.");
        isManualDisconnect = false; // Reset disconnect flag

        // Use the param passed in the constructor directly
        conversation = new OmniRealtimeConversation(param, new OmniRealtimeCallback() {
            @Override
            public void onOpen() {
                log.info("[qwen_omni_realtime_client] Connected Successfully");
                eventProcessor.onNext(new source.hanger.core.extension.bailian.realtime.events.ConnectionOpenedEvent());
            }

            @Override
            public void onEvent(JsonObject message) {
                log.debug("[qwen_omni_realtime_client] Received raw message: {}", message.toString());
                // Parse the JsonObject into a specific RealtimeEvent type
                source.hanger.core.extension.bailian.realtime.events.RealtimeEvent event = parseRealtimeEvent(message);
                eventProcessor.onNext(event);
            }

            public void onError(Exception e) {
                log.error("[qwen_omni_realtime_client] Realtime API Error: {}", e.getMessage(), e);
                eventProcessor.onError(e); // Signal error to the stream
            }

            @Override
            public void onClose(int code, String reason) {
                log.info("[qwen_omni_realtime_client] Connection closed. Code: {}, Reason: {}", code, reason);
                // Push a ConnectionClosedEvent
                eventProcessor.onNext(
                        new source.hanger.core.extension.bailian.realtime.events.ConnectionClosedEvent(code, reason));
                // Clean up disposable if connection is intentionally closed by the client
                if (disposable != null && !disposable.isDisposed()) {
                    disposable.dispose();
                }
                // Attempt to reconnect if not intentionally closed by `disconnect` method
                if (!isManualDisconnect) {
                    log.warn("[qwen_omni_realtime_client] Connection unexpectedly closed. Attempting to reconnect...");
                    executorService.schedule(() -> {
                        try {
                            connect(); // Recursive call to reconnect
                        } catch (Exception e) {
                            log.error("[qwen_omni_realtime_client] Failed to reconnect: {}", e.getMessage(), e);
                            // Optionally, push an error event or terminate the stream
                            eventProcessor.onError(e);
                        }
                    }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
                }
            }
        });

        conversation.connect();
    }

    /**
     * 断开与 Realtime API 的连接。
     *
     * @param code   关闭代码
     * @param reason 关闭原因
     */
    public void disconnect(int code, String reason) {
        log.info("[qwen_omni_realtime_client] Disconnecting from Realtime API. Code: {}, Reason: {}", code, reason);
        isManualDisconnect = true; // Set flag to prevent automatic reconnect
        if (conversation != null) { // Removed isOpen()
            conversation.close(code, reason);
        }
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); // Immediately shut down the executor
        }
        log.info("[qwen_omni_realtime_client] Disconnected.");
    }

    /**
     * 获取 Realtime API 事件的 Flowable。
     * 订阅此 Flowable 以接收来自 Realtime API 的所有事件。
     *
     * @return 包含 JsonObject 事件的 Flowable
     */
    public Flowable<source.hanger.core.extension.bailian.realtime.events.RealtimeEvent> getEvents() {
        return eventProcessor;
    }

    /**
     * 检查客户端是否已连接。
     *
     * @return 如果连接，则为 true，否则为 false。
     */
    public boolean isConnected() {
        return conversation != null; // Changed to check for null only
    }

    /**
     * 获取 OmniRealtimeConversation 实例。
     *
     * @return OmniRealtimeConversation 实例。
     */
    public OmniRealtimeConversation getConversation() {
        return conversation;
    }

    /**
     * 将原始 JsonObject 解析为具体的 RealtimeEvent 类型。
     *
     * @param message 原始的 JsonObject 消息。
     * @return 对应的 RealtimeEvent 实例。
     */
    private source.hanger.core.extension.bailian.realtime.events.RealtimeEvent parseRealtimeEvent(JsonObject message) {
        if (message == null || !message.has("type")) {
            log.warn("[qwen_omni_realtime_client] Received message without 'type' field: {}", message);
            // Return a generic event or null, or throw an exception
            return null; // Or a generic UnknownEvent
        }

        String type = message.get("type").getAsString();
        switch (type) {
            case source.hanger.core.extension.bailian.realtime.events.SessionCreatedEvent.TYPE:
                source.hanger.core.extension.bailian.realtime.events.SessionCreatedEvent sessionCreatedEvent = new Gson()
                        .fromJson(message,
                                source.hanger.core.extension.bailian.realtime.events.SessionCreatedEvent.class);
                return sessionCreatedEvent;
            case source.hanger.core.extension.bailian.realtime.events.ResponseTextDeltaEvent.TYPE:
                source.hanger.core.extension.bailian.realtime.events.ResponseTextDeltaEvent textDeltaEvent = new Gson()
                        .fromJson(message,
                                source.hanger.core.extension.bailian.realtime.events.ResponseTextDeltaEvent.class);
                return textDeltaEvent;
            case source.hanger.core.extension.bailian.realtime.events.ResponseAudioDeltaEvent.TYPE:
                source.hanger.core.extension.bailian.realtime.events.ResponseAudioDeltaEvent audioDeltaEvent = new Gson()
                        .fromJson(message,
                                source.hanger.core.extension.bailian.realtime.events.ResponseAudioDeltaEvent.class);
                return audioDeltaEvent;
            case source.hanger.core.extension.bailian.realtime.events.FunctionCallArgumentsDoneEvent.TYPE:
                source.hanger.core.extension.bailian.realtime.events.FunctionCallArgumentsDoneEvent functionCallEvent = new Gson()
                        .fromJson(message,
                                source.hanger.core.extension.bailian.realtime.events.FunctionCallArgumentsDoneEvent.class);
                return functionCallEvent;
            case source.hanger.core.extension.bailian.realtime.events.InputAudioTranscriptionCompletedEvent.TYPE:
                source.hanger.core.extension.bailian.realtime.events.InputAudioTranscriptionCompletedEvent transcriptionCompletedEvent = new Gson()
                        .fromJson(message,
                                source.hanger.core.extension.bailian.realtime.events.InputAudioTranscriptionCompletedEvent.class);
                return transcriptionCompletedEvent;
            case source.hanger.core.extension.bailian.realtime.events.InputAudioBufferSpeechStartedEvent.TYPE:
                source.hanger.core.extension.bailian.realtime.events.InputAudioBufferSpeechStartedEvent speechStartedEvent = new Gson()
                        .fromJson(message,
                                source.hanger.core.extension.bailian.realtime.events.InputAudioBufferSpeechStartedEvent.class);
                return speechStartedEvent;
            case source.hanger.core.extension.bailian.realtime.events.InputAudioBufferSpeechStoppedEvent.TYPE:
                source.hanger.core.extension.bailian.realtime.events.InputAudioBufferSpeechStoppedEvent speechStoppedEvent = new Gson()
                        .fromJson(message,
                                source.hanger.core.extension.bailian.realtime.events.InputAudioBufferSpeechStoppedEvent.class);
                return speechStoppedEvent;
            default:
                log.warn("[qwen_omni_realtime_client] Unknown event type received: {}", type);
                // Return a generic RealtimeEvent wrapper for unknown types
                return new source.hanger.core.extension.bailian.realtime.events.UnknownRealtimeEvent(message);
        }
    }
}
