//package source.hanger.core.extension.refactor.dashscope.client.realtime;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//
//import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
//import com.alibaba.dashscope.exception.NoApiKeyException;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import io.reactivex.Flowable;
//import lombok.extern.slf4j.Slf4j;
//import source.hanger.core.common.ExtensionConstants;
//import source.hanger.core.extension.api.BaseRealtimeExtension;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ConnectionClosedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ConnectionOpenedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.FunctionCallArgumentsDoneEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.InputAudioBufferCommittedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.InputAudioBufferSpeechStartedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.InputAudioBufferSpeechStoppedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.InputAudioTranscriptionCompletedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ItemCreatedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.RealtimeEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.RealtimeEventType;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseAudioDeltaEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseAudioDoneEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseAudioTranscriptDeltaEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseAudioTranscriptDoneEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseContentPartAddedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseContentPartDoneEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseCreatedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseDoneEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseOutputItemAddedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseOutputItemDoneEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseTextDeltaEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.ResponseTextDoneEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.SessionCreatedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.events.SessionUpdatedEvent;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.messages.FunctionCallOutputItem;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.messages.InputAudioTranscription;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.messages.ItemCreateMessage;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.messages.ItemTruncateMessage;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.messages.ResponseCreateMessage;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.messages.ResponseCreateParams;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.messages.SessionUpdateMessage;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.messages.SessionUpdateParams;
//import source.hanger.core.extension.refactor.dashscope.client.realtime.messages.TurnDetection;
//import source.hanger.core.extension.system.ChatMemory;
//import source.hanger.core.message.CommandResult;
//import source.hanger.core.message.DataMessage;
//import source.hanger.core.message.Message;
//import source.hanger.core.message.MessageType;
//import source.hanger.core.message.command.Command;
//import source.hanger.core.message.command.GenericCommand;
//import source.hanger.core.tenenv.TenEnv;
//
/// **
// * Qwen Omni Realtime 扩展实现。
// * 继承 BaseRealtimeExtension 并实现具体的实时 API 逻辑。
// */
//@Slf4j
//public class QwenOmniRealtimeExtension extends BaseRealtimeExtension {
//
//    // Jackson ObjectMapper for serializing messages to send
//    private final ObjectMapper objectMapper = new ObjectMapper();
//    private QwenOmniRealtimeClient realtimeClient; // Replaced OmniRealtimeConversation
//    private String model;
//    private String voice;
//    private int sampleRate;
//    private boolean audioOut;
//    private boolean inputTranscript;
//    private boolean serverVad;
//    private String vadType; // server_vad or semantic_vad
//    private double vadThreshold;
//    private int vadPrefixPaddingMs;
//    private int vadSilenceDurationMs;
//    private boolean enableStorage; // New: Enable storage from config
//    private String prompt; // New: Prompt from config
//    private ChatMemory chatMemory; // New: Chat memory instance
//    private String lastItemId = ""; // New: To store the item_id for truncation
//    private int lastContentIndex = 0; // New: To store content_index for truncation
//    private long sessionStartTimestampMs = 0; // New: To store session start timestamp
//
//    @Override
//    public void onConfigure(TenEnv env, Map<String, Object> properties) {
//        super.onConfigure(env, properties);
//        log.info("[{}] Extension configuring", env.getExtensionName());
//
//        String apiKey = env.getPropertyString("api_key").orElse("");
//        model = env.getPropertyString("model").orElse("qwen-omni-turbo-realtime-latest");
//        String language = env.getPropertyString("language").orElse("zh-CN");
//        voice = env.getPropertyString("voice").orElse("alloy");
//        sampleRate = env.getPropertyInt("sample_rate").orElse(24000);
//        audioOut = env.getPropertyBool("audio_out").orElse(true);
//        inputTranscript = env.getPropertyBool("input_transcript").orElse(true);
//        serverVad = env.getPropertyBool("server_vad").orElse(true);
//        vadType = env.getPropertyString("vad_type").orElse("server_vad");
//        vadThreshold = env.getPropertyDouble("vad_threshold").orElse(0.5);
//        vadPrefixPaddingMs = env.getPropertyInt("vad_prefix_padding_ms").orElse(300);
//        vadSilenceDurationMs = env.getPropertyInt("vad_silence_duration_ms").orElse(1500); // Corrected default value
//        // New: Max history from config
//        int maxHistory = env.getPropertyInt("max_history").orElse(20);
//        enableStorage = env.getPropertyBool("enable_storage").orElse(false);
//        String systemPrompt = env.getPropertyString("system_prompt").orElse("请用清晰、简洁的语言回复我。在回答问题之前，请充分理解问题。\n");
//        prompt = "%s -（%s）\n".formatted(systemPrompt, env.getPropertyString("prompt").orElse(""));
//
//        log.info("[{}] Config: model={}, language={}, voice={}, sampleRate={}, audioOut={}"
//                + ", inputTranscript={}, serverVad={}, vadType={}, vadThreshold={}, vadPrefixPaddingMs={}"
//                + ", vadSilenceDurationMs={}, maxHistory={}, enableStorage={}",
//            env.getExtensionName(), model, language, voice, sampleRate, audioOut, inputTranscript, serverVad,
//            vadType, vadThreshold,
//            vadPrefixPaddingMs, vadSilenceDurationMs, maxHistory, enableStorage);
//
//        if (apiKey.isEmpty()) {
//            log.error("[{}] API Key is not set. Please configure in manifest.json/property.json.", env
//            .getExtensionName());
//        }
//
//        this.chatMemory = new ChatMemory(maxHistory); // Initialize chat memory after all properties are read
//    }
//
//    @Override
//    public void onInit(TenEnv env) {
//        super.onInit(env);
//        log.info("[{}] Extension initialized", env.getExtensionName());
//    }
//
//    @Override
//    public void onStart(TenEnv env) {
//        super.onStart(env);
//        log.info("[{}] Extension starting", env.getExtensionName());
//        sessionStartTimestampMs = System.currentTimeMillis(); // Initialize session start timestamp here
//
//        // Link ChatMemory callbacks
//        chatMemory.onMemoryExpired(memoryEntry -> {
//            // Implement Python's _on_memory_expired logic
//            // This involves sending ItemDelete if item_id is present.
//            String itemId = (String) memoryEntry.get("id"); // 'id' is the key for item_id in the map
//            if (itemId != null && !itemId.isEmpty()) {
//                try {
//                    ObjectNode itemDelete = objectMapper.createObjectNode();
//                    itemDelete.put("type", "conversation.item.delete");
//                    itemDelete.put("item_id", itemId);
//                    realtimeClient.getConversation().sendRaw(objectMapper.writeValueAsString(itemDelete));
//                    log.info("[{}] Sent ItemDelete for expired memory: {}", env.getExtensionName(), itemId);
//                } catch (Exception e) {
//                    log.error("[{}] Failed to send ItemDelete for expired memory: {}", env.getExtensionName(), e
//                    .getMessage(), e);
//                }
//            }
//        });
//        chatMemory.onMemoryAppended(message -> {
//            // TODO: Implement Python's _on_memory_appended logic
//            // This involves sending Data.create("append") if enable_storage is true.
//            if (enableStorage) {
//                try {
//                    // Python: env.send_data(Data.create("append", content=message))
//                    DataMessage dataMessage = DataMessage.create("append");
//                    dataMessage.setData(objectMapper.writeValueAsBytes(message));
//                    env.sendData(dataMessage);
//                    log.debug("[{}] Sent Data.create(\"append\") for chat memory.", env.getExtensionName());
//                } catch (Exception e) {
//                    log.error("[{}] Failed to send Data.create(\"append\") for chat memory: {}",
//                        env.getExtensionName(), e.getMessage(), e);
//                }
//            } else {
//                log.debug("[{}] Chat memory appended: {} (storage disabled)", env.getExtensionName(), message);
//            }
//        });
//
//        connectToRealtimeApi(env); // This calls realtimeClient.connect()
//
//        // Subscribe to events from the QwenOmniRealtimeClient
//        this.disposable = realtimeClient.getEvents().subscribe(realtimeEvent -> {
//            // Pass the event to the BaseFlushExtension's streamProcessor
//            streamProcessor.onNext(new StreamPayload<>(Flowable.just(realtimeEvent), null));
//        }, throwable -> {
//            log.error("[{}] Error from Realtime API event stream: {}", env.getExtensionName(), throwable.getMessage
//            (), throwable);
//            sendErrorResult(env, null, MessageType.DATA, "RealtimeEventStreamError",
//                "Realtime事件流异常: %s".formatted(throwable.getMessage()));
//        });
//    }
//
//    @Override
//    public void onStop(TenEnv env) {
//        super.onStop(env);
//        log.info("[{}] Extension stopping", env.getExtensionName());
//        if (realtimeClient != null && realtimeClient.isConnected()) {
//            realtimeClient.disconnect(1000, "Extension stopped"); // Use client's disconnect
//        }
//        if (disposable != null && !disposable.isDisposed()) {
//            disposable.dispose();
//        }
//    }
//
//    @Override
//    public void onDeinit(TenEnv env) {
//        super.onDeinit(env);
//        log.info("[{}] Extension de-initialized", env.getExtensionName());
//        if (realtimeClient != null && realtimeClient.isConnected()) {
//            realtimeClient.disconnect(1000, "Extension de-initialized"); // Use client's disconnect
//        }
//        if (disposable != null && !disposable.isDisposed()) {
//            disposable.dispose();
//        }
//    }
//
//    private void connectToRealtimeApi(TenEnv env) {
//        try {
//            // Initialize the QwenOmniRealtimeClient
//            String apiKey = env.getPropertyString("api_key")
//                .orElse(System.getProperty("bailian.dashscope.api.key")); // Fallback to system environment variable
//            String model = (String)this.configuration.getOrDefault("model", "qwen-omni-turbo-realtime-latest");
//            int sampleRate = (Integer)this.configuration.getOrDefault("sample_rate", 16000);
//
//            if (apiKey == null || apiKey.isEmpty()) {
//                throw new NoApiKeyException();
//            }
//
//            OmniRealtimeParam param = OmniRealtimeParam.builder()
//                .model(model)
//                .apikey(apiKey)
//                .build();
//
//            realtimeClient = new QwenOmniRealtimeClient(param);
//            realtimeClient.connect();
//            log.info("[{}] Realtime API connected.", env.getExtensionName());
//        } catch (NoApiKeyException e) {
//            log.error("[{}] DashScope API Key not found: {}", env.getExtensionName(), e.getMessage(), e);
//            throw new RuntimeException("DashScope API Key not found.", e);
//        } catch (Exception e) {
//            log.error("[{}] Failed to connect to Realtime API: {}", env.getExtensionName(), e.getMessage(), e);
//            throw new RuntimeException("Failed to connect to Realtime API.", e);
//        }
//    }
//
//    private void disconnectFromRealtimeApi(int code, String reason) {
//        if (realtimeClient != null) {
//            realtimeClient.disconnect(code, reason);
//            realtimeClient = null;
//        }
//    }
//
//    private ResponseCreateMessage buildResponseCreateMessage(String text, String eventId) {
//        List<String> modalities = new ArrayList<>();
//        modalities.add("text");
//        if (audioOut) {
//            modalities.add("audio");
//        }
//        return ResponseCreateMessage.builder()
//            .type("response.create")
//            .response(ResponseCreateParams.builder()
//                .instructions(text)
//                .modalities(modalities)
//                .build())
//            .eventId(eventId)
//            .build();
//    }
//
//    @Override
//    protected void onSendTextToRealtime(TenEnv env, String text, Message originalMessage) {
//        if (realtimeClient != null && realtimeClient.isConnected()) {
//            try {
//                // 使用 Jackson 将对象转换为 JSON 字符串
//                String eventId = UUID.randomUUID().toString(); // Generate a unique event_id
//                String messageJson = objectMapper.writeValueAsString(buildResponseCreateMessage(text, eventId));
//                realtimeClient.getConversation().sendRaw(messageJson); // 使用 sendRaw 发送构建好的 JSON 字符串
//                log.info("[{}] Sent text as ResponseCreate to Realtime API: eventId={}, text={}", env
//                .getExtensionName(), eventId, text);
//            } catch (Exception e) {
//                log.error("[{}] Failed to send ResponseCreate message to Realtime API: {}", env.getExtensionName(),
//                e.getMessage(), e);
//                sendErrorResult(env, originalMessage.getId(), originalMessage.getType(), originalMessage.getName(),
//                    "发送文本到Realtime API失败: %s".formatted(e.getMessage()));
//            }
//        } else {
//            log.warn("[{}] Realtime client not connected, cannot send text: {}", env.getExtensionName(), text);
//        }
//    }
//
//    @Override
//    protected void onSendAudioToRealtime(TenEnv env, byte[] audioData, Message originalMessage) {
//        if (realtimeClient != null && realtimeClient.isConnected()) {
//            try {
//                log.debug("[{}] Sending audio frame size: {}", env.getExtensionName(), audioData.length);
//                String audioBase64 = java.util.Base64.getEncoder().encodeToString(audioData);
//
//                realtimeClient.getConversation().appendAudio(audioBase64);
//            } catch (Exception e) {
//                log.error("[{}] Failed to send audio to Realtime API: {}", env.getExtensionName(), e.getMessage(), e);
//                sendErrorResult(env, originalMessage.getId(), originalMessage.getType(), originalMessage.getName(),
//                    "发送音频到Realtime API失败: %s".formatted(e.getMessage()));
//            }
//        } else {
//            log.warn("[{}] Realtime client not connected, cannot send audio.", env.getExtensionName());
//        }
//    }
//
//    @Override
//    protected void processRealtimeEvent(TenEnv env, RealtimeEvent event, Message originalMessage) {
//        if (event == null) {
//            log.warn("[{}] Received null RealtimeEvent.", env.getExtensionName());
//            return;
//        }
//
//        String typeString = event.getType();
//        RealtimeEventType eventType = RealtimeEventType.fromValue(typeString);
//        log.debug("[{}] Processing RealtimeEvent of type: {} (Enum: {})", env.getExtensionName(), typeString,
//        eventType);
//
//        switch (eventType) {
//            case SESSION_CREATED:
//                handleSessionCreated(env, (SessionCreatedEvent) event);
//                break;
//            case RESPONSE_TEXT_DELTA:
//                handleResponseTextDelta(env, (ResponseTextDeltaEvent) event);
//                break;
//            case RESPONSE_AUDIO_DELTA:
//                handleResponseAudioDelta(env, (ResponseAudioDeltaEvent) event, originalMessage);
//                break;
//            case RESPONSE_FUNCTION_CALL_ARGUMENTS_DONE:
//                handleFunctionCallArgumentsDone(env, (FunctionCallArgumentsDoneEvent) event, originalMessage);
//                break;
//            case ITEM_INPUT_AUDIO_TRANSCRIPTION_COMPLETED:
//                handleInputAudioTranscriptionCompleted(env, (InputAudioTranscriptionCompletedEvent) event);
//                break;
//            case INPUT_AUDIO_BUFFER_SPEECH_STARTED:
//                handleInputAudioBufferSpeechStarted(env, (InputAudioBufferSpeechStartedEvent) event);
//                break;
//            case INPUT_AUDIO_BUFFER_SPEECH_STOPPED:
//                handleInputAudioBufferSpeechStopped(env, (InputAudioBufferSpeechStoppedEvent) event);
//                break;
//            case CONNECTION_OPENED:
//                handleConnectionOpened(env, (ConnectionOpenedEvent) event);
//                break;
//            case CONNECTION_CLOSED:
//                handleConnectionClosed(env, (ConnectionClosedEvent) event);
//                break;
//            case SESSION_UPDATED:
//                handleSessionUpdated(env, (SessionUpdatedEvent) event);
//                break;
//            case INPUT_AUDIO_BUFFER_COMMITED:
//                handleInputAudioBufferCommitted(env, (InputAudioBufferCommittedEvent) event);
//                break;
//            case CONVERSATION_ITEM_CREATED: // This is ItemCreatedEvent
//                handleItemCreated(env, (ItemCreatedEvent) event);
//                break;
//            case RESPONSE_CREATED:
//                handleResponseCreated(env, (ResponseCreatedEvent) event);
//                break;
//            case RESPONSE_OUTPUT_ITEM_ADDED:
//                handleResponseOutputItemAdded(env, (ResponseOutputItemAddedEvent) event);
//                break;
//            case RESPONSE_CONTENT_PART_ADDED:
//                handleResponseContentPartAdded(env, (ResponseContentPartAddedEvent) event);
//                break;
//            case RESPONSE_AUDIO_TRANSCRIPT_DELTA:
//                handleResponseAudioTranscriptDelta(env, (ResponseAudioTranscriptDeltaEvent) event);
//                break;
//            case RESPONSE_AUDIO_TRANSCRIPT_DONE:
//                handleResponseAudioTranscriptDone(env, (ResponseAudioTranscriptDoneEvent) event);
//                break;
//            case RESPONSE_AUDIO_DONE:
//                handleResponseAudioDone(env, (ResponseAudioDoneEvent) event);
//                break;
//            case RESPONSE_CONTENT_PART_DONE:
//                handleResponseContentPartDone(env, (ResponseContentPartDoneEvent) event);
//                break;
//            case RESPONSE_OUTPUT_ITEM_DONE:
//                handleResponseOutputItemDone(env, (ResponseOutputItemDoneEvent) event);
//                break;
//            case RESPONSE_DONE:
//                handleResponseDone(env, (ResponseDoneEvent) event);
//                break;
//            case RESPONSE_TEXT_DONE: // Handle the newly created ResponseTextDoneEvent
//                handleResponseTextDone(env, (ResponseTextDoneEvent) event);
//                break;
//            case UNKNOWN:
//                log.warn("[{}] Received unknown Realtime event type.", env.getExtensionName());
//                break;
//            default:
//                log.warn("[{}] Unhandled Realtime event type: {}", env.getExtensionName(), typeString);
//                break;
//        }
//    }
//
//    @Override
//    protected void onCancelRealtime(TenEnv env) {
//        log.info("[{}] Cancelling current Realtime API request.", env.getExtensionName());
//        disconnectFromRealtimeApi(1000, "Cancelled by extension");
//    }
//
//    @Override
//    protected void handleRealtimeCommand(TenEnv env, Command command) {
//        // Handle specific commands related to Realtime API, e.g., user join/leave
//        String commandName = command.getName();
//        switch (commandName) {
//            case ExtensionConstants.CMD_IN_ON_USER_JOINED:
//                handleUserJoined(env, command);
//                break;
//            case ExtensionConstants.CMD_IN_ON_USER_LEFT:
//                handleUserLeft(env, command);
//                break;
//            default:
//                log.warn("[{}] Unhandled command: {}", env.getExtensionName(), commandName);
//                CommandResult result = CommandResult.fail(command, "Realtime: 未知命令.");
//                env.sendResult(result);
//                break;
//        }
//    }
//
//    private void handleUserJoined(TenEnv env, Command command) {
//        log.info("[{}] User joined. Sending greeting if configured.", env.getExtensionName());
//        String greeting = env.getPropertyString("greeting").orElse("");
//        if (!greeting.isEmpty()) {
//            try {
//                String eventId = UUID.randomUUID().toString(); // Generate a unique event_id
//                // 使用 Jackson 将对象转换为 JSON 字符串
//                ResponseCreateMessage createMessage = buildResponseCreateMessage("对我说：'%s'".formatted(greeting),
//                eventId);
//                String messageJson = objectMapper.writeValueAsString(createMessage);
//                realtimeClient.getConversation().sendRaw(messageJson);
//
//                log.info("[{}] Greeting [{}] sent to user via ResponseCreate.", env.getExtensionName(), greeting);
//                CommandResult joinResult = CommandResult.success(command, "User joined, greeting handled.");
//                env.sendResult(joinResult);
//            } catch (Exception e) {
//                log.error("[{}] Failed to send greeting via ResponseCreate: {}", env.getExtensionName(), e
//                .getMessage(), e);
//                sendErrorResult(env, command, "发送问候语失败: %s".formatted(e.getMessage()));
//            }
//        } else {
//            CommandResult joinResult = CommandResult.success(command, "User joined, no greeting configured.");
//            env.sendResult(joinResult);
//        }
//    }
//
//    private void handleUserLeft(TenEnv env, Command command) {
//        log.info("[{}] User left. No specific action for now.", env.getExtensionName());
//        CommandResult leftResult = CommandResult.success(command, "User left.");
//        env.sendResult(leftResult);
//    }
//
//    private void sendInitialSessionUpdate(TenEnv env) {
//        try {
//            // Use SDK classes for building SessionUpdate if available
//            // Assuming DashScope SDK provides classes like SessionUpdate or
//            // SessionUpdateParams
//            // based on common SDK patterns and Python's struct.py
//
//            // Modalities
//            List<String> modalities = new ArrayList<>();
//            if (audioOut) {
//                modalities.add("audio");
//            }
//            if (inputTranscript) {
//                modalities.add("text");
//            }
//
//            // Turn Detection (VAD)
//            TurnDetection turnDetection = null;
//            if (serverVad) {
//                turnDetection = TurnDetection.builder()
//                    .type(vadType)
//                    .threshold(vadThreshold)
//                    .prefixPaddingMs(vadPrefixPaddingMs)
//                    .silenceDurationMs(vadSilenceDurationMs)
//                    .build();
//            } else if ("semantic_vad".equals(vadType)) {
//                turnDetection = TurnDetection.builder()
//                    .type(vadType)
//                    .build();
//            }
//
//            // Input Audio Transcription
//            InputAudioTranscription inputAudioTranscription = null;
//            if (inputTranscript) {
//                inputAudioTranscription = InputAudioTranscription.builder()
//                    .model("gummy-realtime-v1")
//                    .build();
//            }
//
//            // Session Update Params
//            SessionUpdateParams sessionParams = SessionUpdateParams.builder()
//                .model(model)
//                .modalities(modalities)
//                .voice(voice)
//                .inputAudioFormat("pcm_16")
//                .outputAudioFormat("pcm_16")
//                .instructions(prompt)
//                .inputAudioTranscription(inputAudioTranscription)
//                .turnDetection(turnDetection)
//                .build();
//
//            // TODO: Tools need to be passed from TenEnv as a property. This is a complex
//            SessionUpdateMessage sessionUpdateMessage = SessionUpdateMessage.builder()
//                .type("session.update")
//                .session(sessionParams)
//                .build();
//
//            // TODO: Re-enable send after clarifying OmniRealtimeConversation's send
//            String messageJson = objectMapper.writeValueAsString(sessionUpdateMessage);
//            realtimeClient.getConversation().sendRaw(messageJson);
//            log.info("[{}] Sent initial session update using structured objects.", env.getExtensionName());
//        } catch (Exception e) {
//            log.error("[{}] Failed to send initial session update: {}", env.getExtensionName(), e.getMessage(), e);
//            sendErrorResult(env, null, MessageType.DATA, "SessionUpdateError", e.getMessage());
//        }
//    }
//
//    // Event handlers for different Realtime API events
//
//    private void handleSessionCreated(TenEnv env, SessionCreatedEvent event) {
//        String sessionId = event.getSession().getId();
//        log.info("[{}] Session Created: {}", env.getExtensionName(), sessionId);
//        // TODO: Send session update based on configuration (voice, modalities, VAD,
//        // tools)
//        // This involves constructing a SessionUpdateParams and sending it via
//        // conversation.send
//        try {
//            sendInitialSessionUpdate(env); // Reuse the method to send session update
//
//            log.info("[{}] Sent session update after session creation.", env.getExtensionName());
//        } catch (Exception e) {
//            log.error("[{}] Failed to send session update after creation: {}", env.getExtensionName(), e.getMessage
//            (), e);
//            sendErrorResult(env, null, MessageType.DATA, "SessionUpdateError", e.getMessage());
//        }
//    }
//
//    private void handleSessionUpdated(TenEnv env, SessionUpdatedEvent event) {
//        String eventId = event.getEventId();
//        String sessionId = event.getSession().getId();
//        log.info("[{}] Session Updated event received: eventId={}, sessionId={}", env.getExtensionName(), eventId,
//        sessionId);
//        // 根据需要处理 session.updated 事件。例如，更新内部状态或记录信息。
//        // 目前，我们只记录日志。在未来，可以根据实际需求添加更复杂的逻辑。
//        // 例如，如果 session.updated 包含新的配置或状态信息，可以将其更新到扩展的属性中。
//    }
//
//    private void handleInputAudioTranscriptionCompleted(TenEnv env, InputAudioTranscriptionCompletedEvent event) {
//        String eventId = event.getEventId();
//        String transcript = event.getTranscript();
//        boolean isFinal = true; // Transcription completed implies final
//        sendAudioTranscriptionText(env, eventId, transcript, isFinal); // Send ASR result as text output
//        log.info("[{}] Input Audio Transcription Completed: eventId={}, transcript={}", env.getExtensionName(),
//        eventId, transcript);
//        chatMemory.put("user", transcript);
//    }
//
//    private void handleResponseTextDelta(TenEnv env, ResponseTextDeltaEvent event) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        String delta = event.getDelta();
//        boolean isFinal = false;
//        sendTextOutput(env, eventId, responseId, delta, isFinal);
//        log.debug("[{}] Response Text Delta: eventId={}, responseId={}, delta={}", env.getExtensionName(), eventId,
//        responseId, delta);
//    }
//
//    private void handleResponseTextDone(TenEnv env, ResponseTextDoneEvent event) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        String text = event.getText();
//        boolean isFinal = true;
//        sendTextOutput(env, eventId, responseId, text, isFinal);
//        log.info("[{}] Response Text Done: eventId={}, responseId={}, text={}", env.getExtensionName(), eventId,
//        responseId, text);
//        // TODO: Update chat memory with assistant message
//        chatMemory.put("assistant", text);
//    }
//
//    private void handleResponseAudioDelta(TenEnv env, ResponseAudioDeltaEvent event, Message originalMessage) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        byte[] audioData = java.util.Base64.getDecoder().decode(event.getDelta());
//        // Assuming 24000 sample rate, 2 bytes per sample, 1 channel for now based on
//        // Python config
//        sendAudioOutput(env, eventId, responseId, audioData, sampleRate, 2, 1);
//        log.debug("[{}] Response Audio Delta received: eventId={}, responseId={}, size={}", env.getExtensionName(),
//        eventId, responseId, audioData.length);
//    }
//
//    private void handleResponseAudioDone(TenEnv env, ResponseAudioDoneEvent event) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        // Python: self._send_response_done()
//        // This means the audio stream from the LLM is complete.
//        // No direct action required here other than logging or state update.
//        log.info("[{}] Response Audio Done: eventId={}, responseId={}", env.getExtensionName(), eventId, responseId);
//    }
//
//    private void handleInputAudioBufferCommitted(TenEnv env, InputAudioBufferCommittedEvent event) {
//        String eventId = event.getEventId();
//        String id = event.getId();
//        Long audioStartMs = event.getAudioStartMs(); // Assuming this is needed or still present
//        Long audioEndMs = event.getAudioEndMs(); // Assuming this is needed or still present
//        log.info("[{}] Input Audio Buffer Committed: eventId={}, id={}, audioStartMs={}, audioEndMs={}", env
//        .getExtensionName(), eventId, id, audioStartMs, audioEndMs);
//    }
//
//    private void handleItemCreated(TenEnv env, ItemCreatedEvent event) {
//        String eventId = event.getEventId();
//        String itemId = event.getItemId();
//        log.info("[{}] Conversation Item Created: eventId={}, itemId={}", env.getExtensionName(), eventId, itemId);
//        lastItemId = itemId;
//        lastContentIndex = 0; // Reset content index for new item
//    }
//
//    private void handleResponseCreated(TenEnv env, ResponseCreatedEvent event) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        String itemId = event.getItemId();
//        log.info("[{}] Response Created: eventId={}, responseId={}, itemId={}", env.getExtensionName(), eventId,
//        responseId, itemId);
//    }
//
//    private void handleResponseOutputItemAdded(TenEnv env, ResponseOutputItemAddedEvent event) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        String itemId = event.getItemId();
//        String outputIndex = event.getOutputIndex();
//        String outputType = event.getOutputType();
//        List<Object> contentParts = event.getContentParts();
//        log.info("[{}] Response Output Item Added: eventId={}, responseId={}, itemId={}, outputIndex={},
//        outputType={}, contentParts={}",
//            env.getExtensionName(), eventId, responseId, itemId, outputIndex, outputType, contentParts);
//        // This event signifies that a new item (e.g., message, function call) has been added to the response output.
//        // In Python, this might trigger _send_response_output_item_start(item_json).
//        // For now, just logging as the subsequent delta/done events will carry content.
//    }
//
//    private void handleResponseContentPartAdded(TenEnv env, ResponseContentPartAddedEvent event) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        String itemId = event.getItemId();
//        String outputIndex = event.getOutputIndex();
//        String contentIndex = event.getContentIndex();
//        String contentType = event.getContentType();
//        String content = event.getContent();
//        log.info("[{}] Response Content Part Added: eventId={}, responseId={}, itemId={}, outputIndex={},
//        contentIndex={}, contentType={}, content={}",
//            env.getExtensionName(), eventId, responseId, itemId, outputIndex, contentIndex, contentType, content);
//        // This event is typically followed by delta events for the content part.
//        // Python's _send_response_content_part_start is called here.
//        // For now, logging, as the content will come via delta events.
//    }
//
//    private void handleResponseAudioTranscriptDelta(TenEnv env, ResponseAudioTranscriptDeltaEvent event) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        String delta = event.getDelta();
//        String itemId = event.getItemId();
//        boolean isFinal = false; // Delta implies not final
//        log.debug("[{}] Response Audio Transcript Delta: eventId={}, responseId={}, itemId={}, delta={}", env
//        .getExtensionName(), eventId, responseId, itemId, delta);
//        // Send this as an ASR result to the client.
//        sendTextOutput(env, eventId, responseId, delta, isFinal); // Reusing sendTextOutput for transcript deltas
//    }
//
//    private void handleResponseAudioTranscriptDone(TenEnv env, ResponseAudioTranscriptDoneEvent event) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        String itemId = event.getItemId();
//        boolean isFinal = true;
//        log.info("[{}] Response Audio Transcript Done: eventId={}, responseId={}, itemId={}, isFinal={}", env
//        .getExtensionName(), eventId, responseId, itemId, isFinal);
//        // This event signifies the end of the audio transcription stream.
//        // No direct action required here, as transcription is handled by delta events.
//    }
//
//    private void handleResponseContentPartDone(TenEnv env, ResponseContentPartDoneEvent event) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        boolean isFinal = true;
//        log.info("[{}] Response Content Part Done: eventId={}, responseId={}, isFinal={}", env.getExtensionName(),
//        eventId, responseId, isFinal);
//        // This event signifies the end of the content part stream.
//        // No direct action required here, as content is handled by delta events.
//    }
//
//    private void handleResponseOutputItemDone(TenEnv env, ResponseOutputItemDoneEvent event) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        boolean isFinal = true;
//        log.info("[{}] Response Output Item Done: eventId={}, responseId={}, isFinal={}", env.getExtensionName(),
//        eventId, responseId, isFinal);
//        // This event signifies the end of the output item stream.
//        // No direct action required here, as output items are handled by delta events.
//    }
//
//    private void handleResponseDone(TenEnv env, ResponseDoneEvent event) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        boolean isFinal = true;
//        log.info("[{}] Response Done: eventId={}, responseId={}, isFinal={}", env.getExtensionName(), eventId,
//        responseId, isFinal);
//        // This event signifies the end of the response stream.
//        // No direct action required here, as response is handled by delta events.
//    }
//
//    private void handleConnectionOpened(TenEnv env, ConnectionOpenedEvent event) {
//        log.info("[{}] Realtime Connection Opened.", env.getExtensionName());
//        // You can add logic here for what happens when the connection is opened,
//        // e.g., send initial session update, or set a flag.
//    }
//
//    private void handleConnectionClosed(TenEnv env, ConnectionClosedEvent event) {
//        log.info("[{}] Realtime Connection Closed. Code: {}, Reason: {}", env.getExtensionName(), event.getCode(),
//            event.getReason());
//        // You can add logic here for what happens when the connection is closed,
//        // e.g., clean up resources, attempt to reconnect (if not handled by client).
//    }
//
//    private void handleFunctionCallArgumentsDone(TenEnv env, FunctionCallArgumentsDoneEvent event,
//        Message originalMessage) {
//        String eventId = event.getEventId();
//        String responseId = event.getResponseId();
//        String toolName = event.getToolName();
//        String functionName = event.getFunctionName();
//        Map<String, Object> arguments = event.getArguments();
//        log.info("[{}] Function Call Arguments Done: eventId={}, responseId={}, toolName={}, functionName={},
//        arguments={}", env.getExtensionName(), eventId, responseId, toolName, functionName, arguments);
//
//        try {
//            // 1. Convert arguments to a Map for the command. (Already Map<String, Object>)
//            // Map<String, Object> argumentsMap = objectMapper.readValue(arguments.toString(), new com.fasterxml
//            .jackson.core.type.TypeReference<Map<String, Object>>() {});
//
//            // 2. Create and send CMD_TOOL_CALL command to TenEnv
//            Command toolCallCommand = GenericCommand.create(ExtensionConstants.CMD_TOOL_CALL);
//            toolCallCommand.getProperties().put(ExtensionConstants.CMD_TOOL_CALL_PROPERTY_NAME,
//                "%s::%s".formatted(toolName, functionName));
//            toolCallCommand.getProperties().put(ExtensionConstants.CMD_TOOL_CALL_PROPERTY_ARGUMENTS, arguments);
//
//            // If originalMessage is null, generate a dummy ID for command result
//            // association.
//            String originalMessageId = originalMessage != null ? originalMessage.getId() : UUID.randomUUID()
//            .toString();
//            toolCallCommand.setId(originalMessageId);
//            toolCallCommand.setParentCommandId(originalMessageId);
//
//            env.sendCmd(toolCallCommand); // env.sendCmd returns void
//
//            // For now, assume success and retrieve result later if needed or via a callback
//            // This part might need asynchronous handling for actual tool result.
//            // Simplified: Direct result handling is removed as sendCmd is void.
//            String resultContentJson = "{\"success\":true}"; // Default success
//
//            // 3. Prepare the FunctionCallOutputItemParam
//            FunctionCallOutputItem functionCallOutputItem = FunctionCallOutputItem.builder()
//                .type("function_call_output")
//                .callId(eventId) // Use eventId as callId
//                .output(arguments) // Using arguments (Map) as output directly, or convert to JSON string if
//                required by API
//                .build();
//
//            // 4. Send ItemCreate with FunctionCallOutputItemParam
//            ItemCreateMessage itemCreateMessage = ItemCreateMessage.builder()
//                .type("conversation.item.create")
//                .item(functionCallOutputItem)
//                .build();
//
//            String itemCreateJson = objectMapper.writeValueAsString(itemCreateMessage);
//            realtimeClient.getConversation().sendRaw(itemCreateJson);
//
//            // 5. Send ResponseCreate
//            ResponseCreateMessage responseCreate = ResponseCreateMessage.builder()
//                .type("response.create")
//                .build();
//
//            String responseCreateJson = objectMapper.writeValueAsString(responseCreate);
//            realtimeClient.getConversation().sendRaw(responseCreateJson);
//
//            log.info("[{}] Tool call handling complete: toolName={}, functionName={}, arguments={}", env
//            .getExtensionName(), toolName, functionName, arguments);
//
//        } catch (Exception e) {
//            log.error("[{}] Failed to handle function call: {}", env.getExtensionName(), e.getMessage(), e);
//            sendErrorResult(env, originalMessage.getId(), originalMessage.getType(), originalMessage.getName(),
//                "处理工具调用失败: " + e.getMessage());
//        }
//    }
//
//    private void handleInputAudioBufferSpeechStarted(TenEnv env, InputAudioBufferSpeechStartedEvent event) {
//        String eventId = event.getEventId();
//        String id = event.getId();
//        Long audioStartMs = event.getAudioStartMs();
//        log.info("[{}] Input Audio Buffer Speech Started: eventId={}, id={}, audioStartMs={}", env.getExtensionName
//        (), eventId, id, audioStartMs);
//
//        long currentMs = System.currentTimeMillis();
//        long audioEndMs = currentMs - sessionStartTimestampMs;
//
//        if (lastItemId != null && !lastItemId.isEmpty() && audioEndMs > 0) {
//            try {
//                ItemTruncateMessage itemTruncateMessage = ItemTruncateMessage.builder()
//                    .type("conversation.item.truncate")
//                    .itemId(lastItemId)
//                    .contentIndex(lastContentIndex)
//                    .audioEndMs(audioEndMs)
//                    .build();
//
//                String messageJson = objectMapper.writeValueAsString(itemTruncateMessage);
//                realtimeClient.getConversation().sendRaw(messageJson);
//
//                log.info("[{}] Sent ItemTruncate for itemId={}, audioEndMs={}", env.getExtensionName(), lastItemId,
//                    audioEndMs);
//            } catch (Exception e) {
//                log.error("[{}] Failed to send ItemTruncate: {}", env.getExtensionName(), e.getMessage(), e);
//            }
//        }
//
//        lastItemId = "";
//        lastContentIndex = 0;
//
//        if (serverVad) {
//            Command flushCommand = GenericCommand.create(ExtensionConstants.CMD_IN_FLUSH);
//            try {
//                env.sendCmd(flushCommand);
//                log.info("[{}] Sent flush command due to server VAD speech started.", env.getExtensionName());
//            } catch (Exception e) {
//                log.error("[{}] Failed to send flush command: {}", env.getExtensionName(), e.getMessage(), e);
//            }
//        }
//    }
//
//    private void handleInputAudioBufferSpeechStopped(TenEnv env, InputAudioBufferSpeechStoppedEvent event) {
//        String eventId = event.getEventId();
//        Long audioEndMs = event.getAudioEndMs();
//        log.info("[{}] Input Audio Buffer Speech Stopped: eventId={}, audioEndMs={}", env.getExtensionName(),
//        eventId, audioEndMs);
//        // TODO: Potentially trigger a flush or other post-speech processing.
//
//        // Python: session_start_ms = int(time.time() * 1000) - message.audio_end_ms
//        sessionStartTimestampMs = System.currentTimeMillis() - audioEndMs;
//        log.info("[{}] sessionStartTimestampMs updated to: {}", env.getExtensionName(), sessionStartTimestampMs);
//
//        // Python has a _flush() call here sometimes, but it's already handled in
//        // handleInputAudioBufferSpeechStarted if serverVad is true.
//        // So no explicit flush needed here unless it's a client-side VAD scenario which
//        // we are not fully supporting yet.
//    }
//}
