package source.hanger.core.extension.bailian.asr;

import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.system.asr.BaseAsrExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.tenenv.TenEnv;

import static java.nio.ByteBuffer.wrap;

@Slf4j
public class ParaformerASRExtension extends BaseAsrExtension {

    private volatile ParaformerASRClient paraformerASRClient; // Concrete client instance

    @Override
    public void onStart(TenEnv env) {
        // Initialize the concrete client here
        String apiKey = env.getPropertyString("api_key").orElse("");
        String model = env.getPropertyString("model").orElse("paraformer-realtime-v2");
        this.paraformerASRClient = new ParaformerASRClient(env.getExtensionName(), apiKey, model);
        log.info("[{}] Initializing ParaformerASRClient with config: apiKey={}, model={}",
            env.getExtensionName(), "***", model);

        super.onStart(env); // Call super.onStart AFTER client is initialized (which now calls
        // startAsrStream)
        log.info("[{}] Paraformer ASR扩展启动.", env.getExtensionName()); // Simplified logging
    }

    @Override
    protected Flowable<RecognitionResult> onRequestAsr(TenEnv env) {
        if (paraformerASRClient == null) {
            throw new IllegalStateException("ParaformerASRClient is not initialized.");
        }
        return paraformerASRClient.startRecognitionStream();
    }

    @Override
    protected void onClientStop() {
        if (paraformerASRClient != null) {
            paraformerASRClient.stopRecognitionClient();
            paraformerASRClient = null; // Clear reference
        }
    }

    @Override
    protected void onClientInit() {
        // Reinitialize the concrete client here
        String apiKey = tenEnv.getPropertyString("api_key").orElse("");
        String model = tenEnv.getPropertyString("model").orElse("paraformer-realtime-v2");
        this.paraformerASRClient = new ParaformerASRClient(tenEnv.getExtensionName(), apiKey, model);
        log.info("[{}] Reinitializing ParaformerASRClient with config: apiKey={}, model={}",
            tenEnv.getExtensionName(), "***", model);
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        super.onAudioFrame(env, audioFrame); // Call super.onAudioFrame if it has shared logic
        if (!isRunning()) { // isRunning is from BaseExtension
            log.warn("[{}] Paraformer ASR扩展未运行，忽略音频帧: frameId={}",
                env.getExtensionName(), audioFrame.getId());
            return;
        }
        final ParaformerASRClient currentClient = this.paraformerASRClient; // Cache volatile variable
        if (currentClient != null) {
            try {
                currentClient.sendAudioFrame(wrap(audioFrame.getBuf()));
            } catch (RuntimeException e) { // Catch RuntimeException from client for reconnect handling
                log.error("[{}] Failed to send audio frame via client: {}, attempting reconnect.",
                    env.getExtensionName(),
                    e.getMessage());
                if (!stopped.get()) { // stopped is from BaseAsrExtension
                    handleReconnect(env);
                }
            }
        } else {
            log.warn("[{}] ASR client is null, cannot send audio frame. Attempting to reconnect.",
                env.getExtensionName());
            if (!stopped.get()) {
                handleReconnect(env);
            }
        }
    }
}
