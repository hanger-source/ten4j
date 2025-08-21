package source.hanger.core.extension.dashscope.client.asr;

import java.io.File;

import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.api.BaseAsrExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.tenenv.TenEnv;

import static java.nio.ByteBuffer.wrap;

@Slf4j
public class ParaformerASRExtension extends BaseAsrExtension {

    private volatile ParaformerASRClient paraformerASRClient; // Concrete client instance
    private AudioFileWriter audioFileWriter; // New instance for audio writing

    @Override
    public void onStart(TenEnv env) {
        // Initialize the concrete client here
        String apiKey = env.getPropertyString("api_key").orElse("");
        String model = env.getPropertyString("model").orElse("paraformer-realtime-v2");
        this.paraformerASRClient = new ParaformerASRClient(env.getExtensionName(), apiKey, model);
        log.info("[{}] Initializing ParaformerASRClient with config: apiKey={}, model={}",
            env.getExtensionName(), "***", model);

        // Initialize AudioFileWriter
        String outputDir = System.getProperty("user.home") + File.separator + "asr_audio_chunks";
        this.audioFileWriter = new AudioFileWriter(env.getExtensionName(), outputDir);
        log.info("[{}] Initialized AudioFileWriter, saving chunks to: {}", env.getExtensionName(), outputDir);

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
        if (audioFileWriter != null) {
            audioFileWriter.close();
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
        // Reinitialize AudioFileWriter on reconnect as well
        String outputDir = System.getProperty("user.home") + File.separator + "asr_audio_chunks";
        this.audioFileWriter = new AudioFileWriter(tenEnv.getExtensionName(), outputDir);
        log.info("[{}] Reinitialized AudioFileWriter on reconnect, saving chunks to: {}", tenEnv.getExtensionName(),
            outputDir);
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        log.debug("[{}] Received audio frame with buffer size: {}", env.getExtensionName(), audioFrame.getBuf().length);
        // Write audio frame to file
        if (audioFileWriter != null) {
            // 记录音频 用于测试音频是否正确接收
            //audioFileWriter.writeAudioFrame(ByteBuffer.wrap(audioFrame.getBuf())); // Wrap byte[] to ByteBuffer
        }

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
