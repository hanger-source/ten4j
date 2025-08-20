package source.hanger.core.extension.system.asr;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;

import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.BaseExtension;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.MessageType;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static source.hanger.core.common.ExtensionConstants.ASR_DATA_OUT_NAME;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_END_OF_SEGMENT;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_ROLE;

@Slf4j
public abstract class BaseAsrExtension extends BaseExtension {

    protected final AtomicBoolean stopped = new AtomicBoolean(false);
    protected final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final CompositeDisposable disposables = new CompositeDisposable(); // 新增：管理所有 Disposable
    protected String sessionId;
    protected TenEnv tenEnv;
    private Disposable asrStreamDisposable;

    @Override
    public void onStart(TenEnv env) {
        log.info("[{}] ASR扩展启动", env.getExtensionName());
        stopped.set(false);
        reconnecting.set(false);
        this.tenEnv = env;
        startAsrStream(env);
        super.onStart(env);
    }

    protected void startAsrStream(TenEnv env) {
        if (asrStreamDisposable != null && !asrStreamDisposable.isDisposed()) {
            asrStreamDisposable.dispose();
        }

        asrStreamDisposable = onRequestAsr(env)
            .observeOn(Schedulers.computation())
            .subscribe(
                item -> {
                    if (item.getSentence() != null) {
                        processRecognitionResultAndSendMessage(item, env);
                    }
                },
                e -> {
                    log.error("[{}] ASR Stream error: {}", env.getExtensionName(), e.getMessage(), e);
                    if (!stopped.get()) {
                        handleReconnect(env);
                    } else {
                        log.info("[{}] Extension stopped, not retrying on stream error.",
                            env.getExtensionName());
                    }
                },
                () -> {
                    log.info("[{}] ASR Stream completed.", env.getExtensionName());
                    if (!stopped.get()) {
                        handleReconnect(env);
                    } else {
                        log.info("[{}] Extension stopped, not reconnecting on stream completion.",
                            env.getExtensionName());
                    }
                });
    }

    private void processRecognitionResultAndSendMessage(RecognitionResult item, TenEnv env) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("text", item.getSentence().getText());
        properties.put("is_final", item.isSentenceEnd());
        properties.put(DATA_OUT_PROPERTY_END_OF_SEGMENT, item.isSentenceEnd());
        properties.put("start_ms", item.getSentence().getBeginTime());
        properties.put("duration_ms",
            item.getSentence().getEndTime() != null && item.getSentence().getBeginTime() != null
                ? item.getSentence().getEndTime() - item.getSentence().getBeginTime()
                : 0L);
        properties.put("language", "zh-CN");
        properties.put("metadata", singletonMap("session_id", this.sessionId));
        properties.put("words", emptyList());

        DataMessage message = DataMessage.create(ASR_DATA_OUT_NAME);
        properties.put(DATA_OUT_PROPERTY_ROLE, "user");
        message.setProperties(properties);
        message.setProperty("asr_request_id", item.getRequestId());
        env.sendData(message);
        log.info("[{}] Sent ASR transcription: {}", env.getExtensionName(), item.getSentence().getText());
    }

    @Override
    public void onDeinit(TenEnv env) {
        disposeAsrStreamsInternal();
        log.info("[{}] ASR扩展清理，停止连接.", env.getExtensionName());
    }

    protected void disposeAsrStreamsInternal() {
        if (asrStreamDisposable != null && !asrStreamDisposable.isDisposed()) {
            asrStreamDisposable.dispose();
        }
        disposables.clear(); // 新增：清理所有定时器 Disposable
        onClientStop();
    }

    protected abstract void onClientStop();

    protected abstract Flowable<RecognitionResult> onRequestAsr(TenEnv env);

    protected synchronized void handleReconnect(TenEnv env) {
        if (reconnecting.get()) {
            log.debug("[{}] Reconnection already in progress, skipping.", env.getExtensionName());
            return;
        }

        reconnecting.set(true);
        log.info("[{}] Starting reconnection process.", env.getExtensionName());

        disposables.add(Flowable.timer(200, TimeUnit.MILLISECONDS, Schedulers.io()).doOnComplete(() -> {
                    try {
                        disposeAsrStreamsInternal();
                        Thread.sleep(200);
                        onClientInit(); // Add this line
                        startAsrStream(env);
                        log.info("[{}] Reconnection completed successfully.", env.getExtensionName());
                    } catch (Exception e) {
                        log.error("[{}] Reconnection failed: {}", env.getExtensionName(), e.getMessage(), e);
                        if (!stopped.get()) {
                            disposables.add( // 新增：将 Disposable 添加到 CompositeDisposable
                                Flowable.timer(1000, TimeUnit.MILLISECONDS, Schedulers.io())
                                    .subscribe(v -> handleReconnect(env)));
                        } else {
                            log.info("[{}] Extension stopped, not retrying reconnection.",
                                env.getExtensionName());
                        }
                    } finally {
                        reconnecting.set(false);
                    }
                })
                .subscribe() // 修正：直接调用 subscribe() 来获取 Disposable
        );
    }

    protected void sendAsrError(TenEnv env, String messageId, MessageType messageType, String messageName,
        String errorMessage) {
        String finalMessageId = (messageId != null && !messageId.isEmpty()) ? messageId
            : MessageUtils.generateUniqueId();
        CommandResult errorResult = CommandResult.fail(finalMessageId, messageType, messageName, errorMessage);
        env.sendResult(errorResult);
        log.error("[{}] ASR Error [{}]: {}", env.getExtensionName(), messageName, errorMessage);
    }

    public boolean isAlive() {
        return !stopped.get();
    }

    public int getInputAudioSampleRate() {
        return 16000;
    }

    // Add this abstract method
    protected abstract void onClientInit();
}
