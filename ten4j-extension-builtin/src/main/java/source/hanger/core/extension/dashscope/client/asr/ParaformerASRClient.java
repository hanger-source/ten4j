package source.hanger.core.extension.dashscope.client.asr;

import java.nio.ByteBuffer;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.exception.NoApiKeyException;

import io.reactivex.Flowable;
import io.reactivex.processors.PublishProcessor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ParaformerASRClient {

    private final String apiKey;
    private final String model;
    private final String name; // For logging purposes, from the extension
    private Recognition recognition;
    private PublishProcessor<ByteBuffer> audioInputProcessor; // New audio input stream

    public ParaformerASRClient(String name, String apiKey, String model) {
        this.name = name;
        this.apiKey = apiKey;
        this.model = model;
        this.audioInputProcessor = PublishProcessor.create(); // Initialize PublishProcessor
    }

    public Flowable<RecognitionResult> startRecognitionStream() {
        if (apiKey == null || apiKey.isEmpty()) {
            return Flowable.error(new IllegalArgumentException("API Key is required for Paraformer ASR."));
        }

        try {
            recognition = new Recognition(); // Create Recognition instance
            RecognitionParam param = RecognitionParam.builder()
                .apiKey(apiKey)
                .model(model)
                .format("pcm")
                .sampleRate(16000)
                .parameter("language_hints", new String[] {"zh", "en"})
                .build();

            // Use streamCall directly
            Flowable<RecognitionResult> resultFlowable = recognition.streamCall(param, audioInputProcessor);

            log.info("[{}] ParaformerASRClient recognition started successfully.", name);
            return resultFlowable;

        } catch (NoApiKeyException e) {
            log.error("[{}] No API Key provided for Paraformer ASR: {}", name, e.getMessage());
            return Flowable.error(e);
        } catch (Exception e) {
            log.error("[{}] Failed to start Paraformer ASR recognition: {}", name, e.getMessage(), e);
            return Flowable.error(e);
        }
    }

    public void sendAudioFrame(ByteBuffer audioFrame) {
        if (audioInputProcessor != null && !audioInputProcessor.hasComplete() && !audioInputProcessor.hasThrowable()) {
            audioInputProcessor.onNext(audioFrame);
        } else {
            log.warn(
                "[{}] Audio input processor is not active, cannot send audio frame. Client not started or already "
                    + "stopped?",
                name);
            throw new IllegalStateException("ParaformerASRClient not active or started.");
        }
    }

    public void stopRecognitionClient() {
        log.info("[{}] Closing ParaformerASRClient recognition client.", name);
        if (recognition != null) {
            try {
                recognition.stop();
                log.info("[{}] ParaformerASRClient recognition client stopped successfully.", name);
            } catch (Exception e) {
                log.warn("[{}] Error stopping ParaformerASRClient recognition client: {}", name, e.getMessage());
            } finally {
                recognition = null; // Clear the reference
            }
        }
        if (audioInputProcessor != null && !audioInputProcessor.hasComplete() && !audioInputProcessor.hasThrowable()) {
            audioInputProcessor.onComplete(); // Complete the audio input stream
            audioInputProcessor = null;
        }
    }
}
