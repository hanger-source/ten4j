package source.hanger.core.extension.system.asr;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;

import io.reactivex.Flowable;
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

@Slf4j
public abstract class BaseAsrExtension extends BaseExtension {

    protected final AtomicBoolean stopped = new AtomicBoolean(false);
    protected final AtomicBoolean reconnecting = new AtomicBoolean(false);
    protected String sessionId;
    private Disposable asrStreamDisposable;

    public void onStart(TenEnv env) {
        log.info("[{}] ASR扩展启动", env.getExtensionName());
        stopped.set(false);
        reconnecting.set(false);
        startAsrStream(env);
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
        properties.put("start_ms", item.getSentence().getBeginTime());
        properties.put("duration_ms",
            item.getSentence().getEndTime() != null && item.getSentence().getBeginTime() != null
                ? item.getSentence().getEndTime() - item.getSentence().getBeginTime()
                : 0L);
        properties.put("language", "zh-CN");
        properties.put("metadata", singletonMap("session_id", this.sessionId));
        properties.put("words", emptyList());

        DataMessage message = DataMessage.create("asr_result");
        message.setProperties(properties);
        env.sendData(message);
        log.debug("[{}] Sent ASR transcription: {}", env.getExtensionName(), item.getSentence().getText());
    }

    public void onDeinit(TenEnv env) {
        disposeAsrStreamsInternal();
        log.info("[{}] ASR扩展清理，停止连接.", env.getExtensionName());
    }

    protected void disposeAsrStreamsInternal() {
        if (asrStreamDisposable != null && !asrStreamDisposable.isDisposed()) {
            asrStreamDisposable.dispose();
        }
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

        Flowable.timer(200, TimeUnit.MILLISECONDS, Schedulers.io())
            .doOnComplete(() -> {
                try {
                    disposeAsrStreamsInternal();
                    Thread.sleep(200);
                    startAsrStream(env);
                    log.info("[{}] Reconnection completed successfully.", env.getExtensionName());
                } catch (Exception e) {
                    log.error("[{}] Reconnection failed: {}", env.getExtensionName(), e.getMessage(), e);
                    if (!stopped.get()) {
                        Flowable.timer(1000, TimeUnit.MILLISECONDS, Schedulers.io())
                            .subscribe(v -> handleReconnect(env));
                    } else {
                        log.info("[{}] Extension stopped, not retrying reconnection.", env.getExtensionName());
                    }
                } finally {
                    reconnecting.set(false);
                }
            })
            .subscribe();
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
}
