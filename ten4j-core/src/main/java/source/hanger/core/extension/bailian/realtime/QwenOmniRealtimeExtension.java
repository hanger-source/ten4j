package source.hanger.core.extension.bailian.realtime;

import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonObject;
import io.reactivex.Flowable;
import io.reactivex.processors.PublishProcessor;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.system.ExtensionConstants;
import source.hanger.core.extension.system.realtime.BaseRealtimeExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import source.hanger.core.extension.system.ChatMemory;

import static source.hanger.core.extension.system.ExtensionConstants.DATA_OUT_PROPERTY_END_OF_SEGMENT;
import static source.hanger.core.extension.system.ExtensionConstants.DATA_OUT_PROPERTY_ROLE;
import static source.hanger.core.extension.system.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;

import java.util.ArrayList;
import java.util.List;
import source.hanger.core.extension.bailian.realtime.events.RealtimeEvent;
import source.hanger.core.extension.bailian.realtime.events.ConnectionClosedEvent;
import source.hanger.core.extension.bailian.realtime.events.ConnectionOpenedEvent;
import source.hanger.core.extension.bailian.realtime.events.FunctionCallArgumentsDoneEvent;
import source.hanger.core.extension.bailian.realtime.events.InputAudioBufferSpeechStartedEvent;
import source.hanger.core.extension.bailian.realtime.events.InputAudioBufferSpeechStoppedEvent;
import source.hanger.core.extension.bailian.realtime.events.InputAudioTranscriptionCompletedEvent;
import source.hanger.core.extension.bailian.realtime.events.ResponseAudioDeltaEvent;
import source.hanger.core.extension.bailian.realtime.events.ResponseTextDeltaEvent;
import source.hanger.core.extension.bailian.realtime.events.SessionCreatedEvent;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.util.UUID;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConstants;
import source.hanger.core.message.command.GenericCommand;

/**
 * Qwen Omni Realtime 扩展实现。
 * 继承 BaseRealtimeExtension 并实现具体的实时 API 逻辑。
 */
@Slf4j
public class QwenOmniRealtimeExtension extends BaseRealtimeExtension {

    private QwenOmniRealtimeClient realtimeClient; // Replaced OmniRealtimeConversation
    private String apiKey;
    private String model;
    private String language;
    private String voice;
    private int sampleRate;
    private boolean audioOut;
    private boolean inputTranscript;
    private boolean serverVad;
    private String vadType; // server_vad or semantic_vad
    private double vadThreshold;
    private int vadPrefixPaddingMs;
    private int vadSilenceDurationMs;
    private int maxHistory; // New: Max history from config
    private boolean enableStorage; // New: Enable storage from config

    private ScheduledExecutorService executorService; // For connection management
    private ChatMemory chatMemory; // New: Chat memory instance
    private String lastItemId = ""; // New: To store the item_id for truncation
    private int lastContentIndex = 0; // New: To store content_index for truncation
    private long sessionStartTimestampMs = 0; // New: To store session start timestamp

    public QwenOmniRealtimeExtension() {
        super();
    }

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        super.onConfigure(env, properties);
        log.info("[qwen_omni_realtime] Extension configuring: {}", env.getExtensionName());

        apiKey = env.getPropertyString("api_key").orElse("");
        model = env.getPropertyString("model").orElse("qwen-omni-turbo-realtime-latest");
        language = env.getPropertyString("language").orElse("zh-CN");
        voice = env.getPropertyString("voice").orElse("alloy");
        sampleRate = env.getPropertyInt("sample_rate").orElse(24000);
        audioOut = env.getPropertyBool("audio_out").orElse(true);
        inputTranscript = env.getPropertyBool("input_transcript").orElse(true);
        serverVad = env.getPropertyBool("server_vad").orElse(true);
        vadType = env.getPropertyString("vad_type").orElse("server_vad");
        vadThreshold = env.getPropertyDouble("vad_threshold").orElse(0.5);
        vadPrefixPaddingMs = env.getPropertyInt("vad_prefix_padding_ms").orElse(300);
        vadSilenceDurationMs = env.getPropertyInt("vad_silence_duration_ms").orElse(1500); // Corrected default value
        maxHistory = env.getPropertyInt("max_history").orElse(20);
        enableStorage = env.getPropertyBool("enable_storage").orElse(false);

        log.info("[qwen_omni_realtime] Config: model={}, language={}, voice={}, sampleRate={}, audioOut={}"
                + ", inputTranscript={}, serverVad={}, vadType={}, vadThreshold={}, vadPrefixPaddingMs={}"
                + ", vadSilenceDurationMs={}, maxHistory={}, enableStorage={}",
                model, language, voice, sampleRate, audioOut, inputTranscript, serverVad, vadType, vadThreshold,
                vadPrefixPaddingMs, vadSilenceDurationMs, maxHistory, enableStorage);

        if (apiKey.isEmpty()) {
            log.error("[qwen_omni_realtime] API Key is not set. Please configure in manifest.json/property.json.");
        }

        this.chatMemory = new ChatMemory(maxHistory); // Initialize chat memory after all properties are read
    }

    @Override
    public void onInit(TenEnv env) {
        super.onInit(env);
        log.info("[qwen_omni_realtime] Extension initialized: {}", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[qwen_omni_realtime] Extension starting: {}", env.getExtensionName());
        sessionStartTimestampMs = System.currentTimeMillis(); // Initialize session start timestamp here

        // Link ChatMemory callbacks
        chatMemory.onMemoryExpired(message -> {
            // TODO: Implement Python's _on_memory_expired logic
            // This involves sending ItemDelete if item_id is present.
            String itemId = (String) message.get("id"); // Assuming 'id' is the key for item_id in the map
            if (itemId != null && !itemId.isEmpty()) {
                try {
                    JsonObject itemDelete = new JsonObject();
                    itemDelete.addProperty("type", "conversation.item.delete");
                    itemDelete.addProperty("item_id", itemId);
                    // TODO: Re-enable send after clarifying OmniRealtimeConversation's send
                    // mechanism
                    // realtimeClient.getConversation().send(itemDelete.toString());
                    log.info("[qwen_omni_realtime] Sent ItemDelete for expired memory: {}", itemId);
                } catch (Exception e) {
                    log.error("[qwen_omni_realtime] Failed to send ItemDelete for expired memory: {}", e.getMessage(),
                            e);
                }
            }
        });
        chatMemory.onMemoryAppended(message -> {
            // TODO: Implement Python's _on_memory_appended logic
            // This involves sending Data.create("append") if enable_storage is true.
            if (enableStorage) {
                try {
                    // Python: env.send_data(Data.create("append", content=message))
                    DataMessage dataMessage = DataMessage.create("append");
                    dataMessage.setData(new Gson().toJson(message).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    env.sendData(dataMessage);
                    log.debug("[qwen_omni_realtime] Sent Data.create(\"append\") for chat memory.");
                } catch (Exception e) {
                    log.error("[qwen_omni_realtime] Failed to send Data.create(\"append\") for chat memory: {}",
                            e.getMessage(), e);
                }
            } else {
                log.debug("[qwen_omni_realtime] Chat memory appended: {} (storage disabled)", message);
            }
        });

        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.submit(() -> connectToRealtimeApi(env)); // This calls realtimeClient.connect()
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
        log.info("[qwen_omni_realtime] Extension stopping: {}", env.getExtensionName());
        if (realtimeClient != null && realtimeClient.isConnected()) {
            realtimeClient.disconnect(1000, "Extension stopped"); // Use client's disconnect
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
        log.info("[qwen_omni_realtime] Extension de-initialized: {}", env.getExtensionName());
        if (realtimeClient != null && realtimeClient.isConnected()) {
            realtimeClient.disconnect(1000, "Extension de-initialized"); // Use client's disconnect
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    private void connectToRealtimeApi(TenEnv env) {
        try {
            // Initialize the QwenOmniRealtimeClient
            String apiKey = env.getPropertyString("dashscope.api_key")
                    .orElse(System.getenv("DASHSCOPE_API_KEY")); // Fallback to system environment variable
            String model = (String) this.configuration.getOrDefault("model", "qwen-omni-turbo-realtime-latest");
            int sampleRate = (Integer) this.configuration.getOrDefault("sample_rate", 16000);

            if (apiKey == null || apiKey.isEmpty()) {
                throw new NoApiKeyException();
            }

            OmniRealtimeParam param = OmniRealtimeParam.builder()
                    .model(model)
                    .apikey(apiKey)
                    .build();

            realtimeClient = new QwenOmniRealtimeClient(param);
            realtimeClient.connect();
            log.info("[qwen_omni_realtime] Realtime API connected.");
        } catch (NoApiKeyException e) {
            log.error("[qwen_omni_realtime] DashScope API Key not found: {}", e.getMessage(), e);
            throw new RuntimeException("DashScope API Key not found.", e);
        } catch (Exception e) {
            log.error("[qwen_omni_realtime] Failed to connect to Realtime API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to connect to Realtime API.", e);
        }
    }

    private void disconnectFromRealtimeApi(int code, String reason) {
        if (realtimeClient != null) {
            realtimeClient.disconnect(code, reason);
            realtimeClient = null;
        }
    }

    @Override
    protected void onSendTextToRealtime(TenEnv env, String text, Message originalMessage) {
        if (realtimeClient != null && realtimeClient.isConnected()) {
            try {
                // This is a placeholder. Actual text sending depends on
                // OmniRealtimeConversation API.
                // OmniRealtimeConversation typically takes audio/video/image buffers.
                // Text is usually part of the initial session or through specific message
                // types.
                // We need to refer to DashScope OmniRealtime API for exact text sending.
                log.warn("[qwen_omni_realtime] Text sending not directly supported by current " +
                        "OmniRealtimeConversation API for stream. Text: {}",
                        text);
                // If text needs to be sent as part of an item, it should be done differently.
                // Example: conversation.send(ItemCreate.builder().item(...).build());
                // For now, let's send it as a regular item create message
                JsonObject itemCreate = new JsonObject();
                itemCreate.addProperty("type", "conversation.item.create");
                JsonObject userMessageItemParam = new JsonObject();
                userMessageItemParam.addProperty("type", "message");
                userMessageItemParam.addProperty("role", "user");

                com.google.gson.JsonArray contentArray = new com.google.gson.JsonArray();
                JsonObject contentPart = new JsonObject();
                contentPart.addProperty("type", "input_text");
                contentPart.addProperty("text", text);
                contentArray.add(contentPart);
                userMessageItemParam.add("content", contentArray);

                itemCreate.add("item", userMessageItemParam);
                // TODO: Re-enable send after clarifying OmniRealtimeConversation's send
                // mechanism
                // realtimeClient.getConversation().send(itemCreate.toString());

            } catch (Exception e) {
                log.error("[qwen_omni_realtime] Failed to send JSON message to Realtime API: {}", e.getMessage(), e);
                sendErrorResult(env, originalMessage.getId(), originalMessage.getType(), originalMessage.getName(),
                        "发送文本到Realtime API失败: " + e.getMessage());
            }
        } else {
            log.warn("[qwen_omni_realtime] Realtime client not connected, cannot send text: {}", text);
        }
    }

    @Override
    protected void onSendAudioToRealtime(TenEnv env, byte[] audioData, Message originalMessage) {
        if (realtimeClient != null && realtimeClient.isConnected()) {
            try {
                // The DashScope SDK example shows `conversation.sendAudio(ByteBuffer
                // audioData)`, but the exact message format
                // for properties like sample rate, channels, etc. might be needed or handled by
                // the SDK implicitly.
                // Based on Python `send_audio_data`, it expects raw PCM data.
                log.debug("[qwen_omni_realtime] Sending audio frame size: {}", audioData.length);
                // TODO: Re-enable sendAudio after clarifying OmniRealtimeConversation's
                // sendAudio mechanism
                // realtimeClient.getConversation().sendAudio(ByteBuffer.wrap(audioData));
            } catch (Exception e) {
                log.error("[qwen_omni_realtime] Failed to send audio to Realtime API: {}", e.getMessage(), e);
                sendErrorResult(env, originalMessage.getId(), originalMessage.getType(), originalMessage.getName(),
                        "发送音频到Realtime API失败: " + e.getMessage());
            }
        } else {
            log.warn("[qwen_omni_realtime] Realtime client not connected, cannot send audio.");
        }
    }

    @Override
    protected void processRealtimeEvent(TenEnv env, RealtimeEvent event, Message originalMessage) {
        if (event == null) {
            log.warn("[qwen_omni_realtime] Received null RealtimeEvent.");
            return;
        }

        String type = event.getType();
        log.debug("[qwen_omni_realtime] Processing RealtimeEvent of type: {}", type);

        switch (type) {
            case SessionCreatedEvent.TYPE:
                handleSessionCreated(env, (SessionCreatedEvent) event); // Cast to specific event type
                break;
            case ResponseTextDeltaEvent.TYPE:
                handleResponseTextDelta(env, (ResponseTextDeltaEvent) event, originalMessage);
                break;
            case ResponseAudioDeltaEvent.TYPE:
                handleResponseAudioDelta(env, (ResponseAudioDeltaEvent) event, originalMessage);
                break;
            case FunctionCallArgumentsDoneEvent.TYPE:
                handleFunctionCallArgumentsDone(env, (FunctionCallArgumentsDoneEvent) event, originalMessage);
                break;
            case InputAudioTranscriptionCompletedEvent.TYPE:
                handleInputAudioTranscriptionCompleted(env, (InputAudioTranscriptionCompletedEvent) event,
                        originalMessage);
                break;
            case InputAudioBufferSpeechStartedEvent.TYPE:
                handleInputAudioBufferSpeechStarted(env, (InputAudioBufferSpeechStartedEvent) event);
                break;
            case InputAudioBufferSpeechStoppedEvent.TYPE:
                handleInputAudioBufferSpeechStopped(env, (InputAudioBufferSpeechStoppedEvent) event);
                break;
            case ConnectionOpenedEvent.TYPE:
                handleConnectionOpened(env, (ConnectionOpenedEvent) event);
                break;
            case ConnectionClosedEvent.TYPE:
                handleConnectionClosed(env, (ConnectionClosedEvent) event);
                break;
            case source.hanger.core.extension.bailian.realtime.events.UnknownRealtimeEvent.TYPE:
                // Handle unknown events, perhaps log a warning or send a generic error
                log.warn("[qwen_omni_realtime] Received unknown Realtime event type: {}",
                        ((source.hanger.core.extension.bailian.realtime.events.UnknownRealtimeEvent) event)
                                .getRawMessage());
                break;
            default:
                log.warn("[qwen_omni_realtime] Unhandled Realtime event type: {}", type);
                break;
        }
    }

    @Override
    protected void onCancelRealtime(TenEnv env) {
        log.info("[qwen_omni_realtime] Cancelling current Realtime API request.");
        disconnectFromRealtimeApi(1000, "Cancelled by extension");
    }

    @Override
    protected void handleRealtimeCommand(TenEnv env, Command command) {
        // Handle specific commands related to Realtime API, e.g., user join/leave
        String commandName = command.getName();
        switch (commandName) {
            case ExtensionConstants.CMD_IN_ON_USER_JOINED:
                handleUserJoined(env, command);
                break;
            case ExtensionConstants.CMD_IN_ON_USER_LEFT:
                handleUserLeft(env, command);
                break;
            default:
                log.warn("[qwen_omni_realtime] Unhandled command: {}", commandName);
                CommandResult result = CommandResult.fail(command, "Realtime: 未知命令.");
                env.sendResult(result);
                break;
        }
    }

    private void handleUserJoined(TenEnv env, Command command) {
        log.info("[qwen_omni_realtime] User joined. Sending greeting if configured.");
        String greeting = command.getPropertyString("greeting").orElse("");
        if (!greeting.isEmpty()) {
            try {
                // Use SDK-like class if available, otherwise fallback to JsonObject
                // Python: ResponseCreate(response=ResponseCreateParams(instructions=...,
                // modalities=["text", "audio"]))
                JsonObject responseCreateJson = new JsonObject();
                responseCreateJson.addProperty("type", "response.create");

                JsonObject responseParams = new JsonObject();
                responseParams.addProperty("instructions", greeting);

                com.google.gson.JsonArray modalitiesArray = new com.google.gson.JsonArray();
                if (audioOut) {
                    modalitiesArray.add("audio");
                }
                if (inputTranscript) {
                    modalitiesArray.add("text");
                }
                responseParams.add("modalities", modalitiesArray);

                responseCreateJson.add("response", responseParams);
                // TODO: Re-enable send after clarifying OmniRealtimeConversation's send
                // mechanism
                // realtimeClient.getConversation().send(responseCreateJson.toString());

                log.info("[qwen_omni_realtime] Greeting [{}] sent to user via ResponseCreate.", greeting);
                CommandResult joinResult = CommandResult.success(command, "User joined, greeting handled.");
                env.sendResult(joinResult);
            } catch (Exception e) {
                log.error("[qwen_omni_realtime] Failed to send greeting via ResponseCreate: {}", e.getMessage(), e);
                sendErrorResult(env, command, "发送问候语失败: " + e.getMessage());
            }
        } else {
            CommandResult joinResult = CommandResult.success(command, "User joined, no greeting configured.");
            env.sendResult(joinResult);
        }
    }

    private void handleUserLeft(TenEnv env, Command command) {
        log.info("[qwen_omni_realtime] User left. No specific action for now.");
        CommandResult leftResult = CommandResult.success(command, "User left.");
        env.sendResult(leftResult);
    }

    private void sendInitialSessionUpdate(TenEnv env) {
        try {
            // Use SDK classes for building SessionUpdate if available
            // Assuming DashScope SDK provides classes like SessionUpdate or
            // SessionUpdateParams
            // based on common SDK patterns and Python's struct.py

            // Modalities
            List<String> modalities = new ArrayList<>();
            if (audioOut) {
                modalities.add("audio");
            }
            if (inputTranscript) {
                modalities.add("text");
            }

            // Turn Detection (VAD)
            JsonObject turnDetection = new JsonObject();
            if (serverVad) {
                turnDetection.addProperty("type", vadType);
                turnDetection.addProperty("threshold", vadThreshold);
                turnDetection.addProperty("prefix_padding_ms", vadPrefixPaddingMs);
                turnDetection.addProperty("silence_duration_ms", vadSilenceDurationMs);
            } else if ("semantic_vad".equals(vadType)) {
                turnDetection.addProperty("type", vadType);
                // Add eagerness if available in config and supported by API
            }

            // Input Audio Transcription
            JsonObject inputAudioTranscription = new JsonObject();
            if (inputTranscript) {
                inputAudioTranscription.addProperty("model", "gummy-realtime-v1");
            }

            // Session Update Params
            JsonObject sessionParams = new JsonObject();
            sessionParams.addProperty("model", model);
            sessionParams.add("modalities", new Gson().toJsonTree(modalities).getAsJsonArray());
            sessionParams.addProperty("voice", voice);
            sessionParams.addProperty("input_audio_format", "pcm_16");
            sessionParams.addProperty("output_audio_format", "pcm_16");
            sessionParams.add("input_audio_transcription", inputAudioTranscription);
            sessionParams.add("turn_detection", turnDetection);

            // TODO: Tools need to be passed from TenEnv as a property. This is a complex
            // part.
            // The Python code retrieves available_tools. This needs a mechanism to get
            // registered tools in Java.
            // For now, skip tools, but this is a critical part for full functionality.

            // Construct the final session.update JSON object
            JsonObject sessionUpdateJson = new JsonObject();
            sessionUpdateJson.addProperty("type", "session.update");
            sessionUpdateJson.add("session", sessionParams);

            // TODO: Re-enable send after clarifying OmniRealtimeConversation's send
            // mechanism
            // realtimeClient.getConversation().send(sessionUpdateJson.toString());
            log.info("[qwen_omni_realtime] Sent initial session update using JsonObject.");
        } catch (Exception e) {
            log.error("[qwen_omni_realtime] Failed to send initial session update: {}", e.getMessage(), e);
            sendErrorResult(env, null, MessageType.DATA, "SessionUpdateError", e.getMessage());
        }
    }

    // Event handlers for different Realtime API events

    private void handleSessionCreated(TenEnv env, SessionCreatedEvent event) {
        String sessionId = event.getSession().getId();
        log.info("[qwen_omni_realtime] Session Created: {}", sessionId);
        // TODO: Send session update based on configuration (voice, modalities, VAD,
        // tools)
        // This involves constructing a SessionUpdateParams and sending it via
        // conversation.send
        try {
            // 构建 SessionUpdateParams
            // This logic is mostly duplicated from sendInitialSessionUpdate, so it's better
            // to reuse it.
            sendInitialSessionUpdate(env); // Reuse the method to send session update

            log.info("[qwen_omni_realtime] Sent session update after session creation.");
        } catch (Exception e) {
            log.error("[qwen_omni_realtime] Failed to send session update after creation: {}", e.getMessage(), e);
            sendErrorResult(env, null, MessageType.DATA, "SessionUpdateError", e.getMessage());
        }
    }

    private void handleInputAudioTranscriptionCompleted(TenEnv env, InputAudioTranscriptionCompletedEvent event,
            Message originalMessage) {
        String transcript = event.getTranscript();
        boolean isFinal = true; // Transcription completed implies final
        sendTextOutput(env, originalMessage, transcript, isFinal); // Send ASR result as text output
        log.info("[qwen_omni_realtime] Input Audio Transcription Completed: {}", transcript);
        chatMemory.put("user", transcript);
    }

    private void handleResponseTextDelta(TenEnv env, ResponseTextDeltaEvent event, Message originalMessage) {
        String delta = event.getDelta();
        boolean isFinal = false;
        sendTextOutput(env, originalMessage, delta, isFinal);
        log.debug("[qwen_omni_realtime] Response Text Delta: {}", delta);
        // The `sendTextOutput` already sends data. Accumulation for memory should
        // happen before that if needed.
        // For now, let's assume the full text is available at ResponseTextDone.
    }

    private void handleResponseTextDone(TenEnv env, JsonObject event, Message originalMessage) {
        String text = event.get("text").getAsString();
        boolean isFinal = true;
        sendTextOutput(env, originalMessage, text, isFinal);
        log.info("[qwen_omni_realtime] Response Text Done: {}", text);
        // TODO: Update chat memory with assistant message
        chatMemory.put("assistant", text);
    }

    private void handleResponseAudioDelta(TenEnv env, ResponseAudioDeltaEvent event, Message originalMessage) {
        String base64Audio = event.getAudioData();
        byte[] audioData = java.util.Base64.getDecoder().decode(base64Audio);
        // Assuming 24000 sample rate, 2 bytes per sample, 1 channel for now based on
        // Python config
        sendAudioOutput(env, originalMessage, audioData, sampleRate, 2, 1);
        log.debug("[qwen_omni_realtime] Response Audio Delta received, size: {}", audioData.length);
    }

    private void handleResponseAudioDone(TenEnv env, JsonObject event, Message originalMessage) {
        // Python: self._send_response_done()
        // This means the audio stream from the LLM is complete.
        // No direct action required here other than logging or state update.
        log.info("[qwen_omni_realtime] Response Audio Done.");
    }

    private void handleConnectionOpened(TenEnv env, ConnectionOpenedEvent event) {
        log.info("[qwen_omni_realtime] Realtime Connection Opened.");
        // You can add logic here for what happens when the connection is opened,
        // e.g., send initial session update, or set a flag.
    }

    private void handleConnectionClosed(TenEnv env, ConnectionClosedEvent event) {
        log.info("[qwen_omni_realtime] Realtime Connection Closed. Code: {}, Reason: {}", event.getCode(),
                event.getReason());
        // You can add logic here for what happens when the connection is closed,
        // e.g., clean up resources, attempt to reconnect (if not handled by client).
    }

    private void handleFunctionCallArgumentsDone(TenEnv env, FunctionCallArgumentsDoneEvent event,
            Message originalMessage) {
        String callId = event.getCallId();
        String name = event.getName();
        JsonElement arguments = event.getArguments();
        log.info("[qwen_omni_realtime] Function Call Arguments Done: callId={}, name={}, arguments={}", callId, name,
                arguments);

        try {
            // 1. Convert arguments to a Map for the command.
            Map<String, Object> argumentsMap = new Gson().fromJson(arguments, new TypeToken<Map<String, Object>>() {
            }.getType());

            // 2. Create and send CMD_TOOL_CALL command to TenEnv
            Command toolCallCommand = GenericCommand.create(ExtensionConstants.CMD_TOOL_CALL);
            toolCallCommand.getProperties().put(ExtensionConstants.CMD_TOOL_CALL_PROPERTY_NAME, name);
            toolCallCommand.getProperties().put(ExtensionConstants.CMD_TOOL_CALL_PROPERTY_ARGUMENTS, argumentsMap);

            // If originalMessage is null, generate a dummy ID for command result
            // association.
            String originalMessageId = originalMessage != null ? originalMessage.getId() : UUID.randomUUID().toString();
            toolCallCommand.setId(originalMessageId);
            toolCallCommand.setParentCommandId(originalMessageId);

            env.sendCmd(toolCallCommand); // env.sendCmd returns void

            // For now, assume success and retrieve result later if needed or via a callback
            // This part might need asynchronous handling for actual tool result.
            // Simplified: Direct result handling is removed as sendCmd is void.
            String resultContentJson = "{\"success\":true}"; // Default success

            // 3. Prepare the FunctionCallOutputItemParam
            JsonObject functionCallOutputItem = new JsonObject();
            functionCallOutputItem.addProperty("type", "function_call_output");
            functionCallOutputItem.addProperty("call_id", callId);
            functionCallOutputItem.addProperty("output", resultContentJson); // Use the (assumed) result

            // 4. Send ItemCreate with FunctionCallOutputItemParam
            JsonObject itemCreate = new JsonObject();
            itemCreate.addProperty("type", "conversation.item.create");
            itemCreate.add("item", functionCallOutputItem);
            // TODO: Re-enable send after clarifying OmniRealtimeConversation's send
            // mechanism
            // realtimeClient.getConversation().send(itemCreate.toString());

            // 5. Send ResponseCreate
            JsonObject responseCreate = new JsonObject();
            responseCreate.addProperty("type", "response.create");
            // TODO: Re-enable send after clarifying OmniRealtimeConversation's send
            // mechanism
            // realtimeClient.getConversation().send(responseCreate.toString());

            log.info("[qwen_omni_realtime] Tool call handling complete: name={}, arguments={}", name, arguments);

        } catch (Exception e) {
            log.error("[qwen_omni_realtime] Failed to handle function call: {}", e.getMessage(), e);
            sendErrorResult(env, originalMessage.getId(), originalMessage.getType(), originalMessage.getName(),
                    "处理工具调用失败: " + e.getMessage());
        }
    }

    private void handleInputAudioBufferSpeechStarted(TenEnv env, InputAudioBufferSpeechStartedEvent event) {
        String itemId = event.getId(); // Corrected: use getId() instead of getItemId()
        Long audioStartMs = event.getAudioStartMs();
        log.info("[qwen_omni_realtime] Input Audio Buffer Speech Started: itemId={}, audioStartMs={}", itemId,
                audioStartMs);
        // TODO: Implement truncation logic similar to Python ItemTruncate
        // This might involve sending ItemTruncate via conversation.send

        // Python: current_ms = int(time.time() * 1000)
        // Python: end_ms = current_ms - session_start_ms
        long currentMs = System.currentTimeMillis();
        long audioEndMs = currentMs - sessionStartTimestampMs;

        if (!lastItemId.isEmpty() && audioEndMs > 0) {
            try {
                // Construct ItemTruncate message
                JsonObject itemTruncate = new JsonObject();
                itemTruncate.addProperty("type", "conversation.item.truncate");
                JsonObject truncateParams = new JsonObject();
                truncateParams.addProperty("item_id", lastItemId);
                truncateParams.addProperty("content_index", lastContentIndex); // Use lastContentIndex from the previous
                                                                               // response
                truncateParams.addProperty("audio_end_ms", audioEndMs);
                itemTruncate.add("item", truncateParams); // In Python, item_id, content_index, audio_end_ms are direct
                                                          // properties of ItemTruncate

                // Re-evaluate the structure based on Python's struct.py: `ItemTruncate` has
                // direct properties `item_id`, `content_index`, `audio_end_ms`
                // Corrected structure:
                JsonObject correctItemTruncate = new JsonObject();
                correctItemTruncate.addProperty("type", "conversation.item.truncate");
                correctItemTruncate.addProperty("item_id", lastItemId);
                correctItemTruncate.addProperty("content_index", lastContentIndex);
                correctItemTruncate.addProperty("audio_end_ms", audioEndMs);

                // TODO: Re-enable send after clarifying OmniRealtimeConversation's send
                // mechanism
                // realtimeClient.getConversation().send(correctItemTruncate.toString());
                log.info("[qwen_omni_realtime] Sent ItemTruncate for itemId={}, audioEndMs={}", lastItemId,
                        audioEndMs);
            } catch (Exception e) {
                log.error("[qwen_omni_realtime] Failed to send ItemTruncate: {}", e.getMessage(), e);
            }
        }

        // Reset lastItemId and lastContentIndex after truncation or speech started.
        // Python sets item_id = "" after this. This implies a new segment will start.
        lastItemId = "";
        lastContentIndex = 0;

        // Python: if self.config.server_vad: await self._flush()
        // This means sending a flush command.
        if (serverVad) {
            Command flushCommand = GenericCommand.create(ExtensionConstants.CMD_IN_FLUSH); // Reusing CMD_IN_FLUSH
            try {
                env.sendCmd(flushCommand);
                log.info("[qwen_omni_realtime] Sent flush command due to server VAD speech started.");
            } catch (Exception e) {
                log.error("[qwen_omni_realtime] Failed to send flush command: {}", e.getMessage(), e);
            }
        }
    }

    private void handleInputAudioBufferSpeechStopped(TenEnv env, InputAudioBufferSpeechStoppedEvent event) {
        Long audioEndMs = event.getAudioEndMs();
        log.info("[qwen_omni_realtime] Input Audio Buffer Speech Stopped: audioEndMs={}", audioEndMs);
        // TODO: Potentially trigger a flush or other post-speech processing.

        // Python: session_start_ms = int(time.time() * 1000) - message.audio_end_ms
        sessionStartTimestampMs = System.currentTimeMillis() - audioEndMs;
        log.info("[qwen_omni_realtime] sessionStartTimestampMs updated to: {}", sessionStartTimestampMs);

        // Python has a _flush() call here sometimes, but it's already handled in
        // handleInputAudioBufferSpeechStarted if serverVad is true.
        // So no explicit flush needed here unless it's a client-side VAD scenario which
        // we are not fully supporting yet.
    }
}
