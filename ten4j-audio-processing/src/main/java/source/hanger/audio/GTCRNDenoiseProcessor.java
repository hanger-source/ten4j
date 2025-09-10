package source.hanger.audio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.k2fsa.sherpa.onnx.DenoisedAudio;
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiser;
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserGtcrnModelConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GTCRNDenoiseProcessor {

    // GTCRN 模型期望的音频参数 (根据 GTCRN 项目 README 和 ONNX 模型确认)
    public static final int GTCRN_SAMPLE_RATE = 16000; // 模型期望的采样率，固定为 16000 Hz
    // private static final int GTCRN_FRAME_LENGTH_SAMPLES = 480; // 30ms at 16kHz

    private OfflineSpeechDenoiser denoiser;
    private OfflineSpeechDenoiserConfig denoiserConfig;

    private String modelResourcePath; // GTCRN ONNX 模型在 resources 中的路径
    private int inputSampleRate; // 实际传入音频的采样率
    private int inputChannels;   // 实际传入音频的声道数

    // 内部音频缓冲区，用于累积原始音频样本
    // private final List<Float> audioBuffer;
    // 内部缓冲区，用于累积降噪后的浮点样本
    // private final List<Float> denoisedOutputBuffer;

    public GTCRNDenoiseProcessor(String modelResourcePath, int inputSampleRate, int inputChannels) {
        this.modelResourcePath = modelResourcePath;
        this.inputSampleRate = inputSampleRate;
        this.inputChannels = inputChannels;
        // this.audioBuffer = new LinkedList<>();
        // this.denoisedOutputBuffer = new LinkedList<>();
    }

    /**
     * 初始化 sherpa-onnx 语音增强器。
     * @throws Exception 如果初始化失败。
     */
    public void init() throws Exception {
        log.info("[GTCRN_PROCESSOR] Initializing sherpa-onnx speech denoiser from resource: {}", modelResourcePath);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(modelResourcePath)) {
            if (is == null) {
                throw new FileNotFoundException("ONNX model resource not found: " + modelResourcePath);
            }
            // 将 classpath 资源转换为文件系统路径
            File tempModelFile = File.createTempFile("gtcrn_simple", ".onnx");
            tempModelFile.deleteOnExit(); // 确保在 JVM 关闭时删除临时文件

            Files.copy(is, tempModelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            String modelFilePath = tempModelFile.getAbsolutePath();
            log.info("[GTCRN_PROCESSOR] ONNX model extracted to temporary file: {}", modelFilePath);

            OfflineSpeechDenoiserGtcrnModelConfig gtcrnModelConfig = new OfflineSpeechDenoiserGtcrnModelConfig.Builder()
                .setModel(modelFilePath)
                .build();

            OfflineSpeechDenoiserModelConfig denoiserModelConfig = new OfflineSpeechDenoiserModelConfig.Builder()
                .setGtcrn(gtcrnModelConfig)
                .setDebug(true) // 启用调试日志
                .build();

            denoiserConfig = new OfflineSpeechDenoiserConfig.Builder()
                .setModel(denoiserModelConfig)
                .build();

            denoiser = new OfflineSpeechDenoiser(denoiserConfig);
            log.info("[GTCRN_PROCESSOR] sherpa-onnx OfflineSpeechDenoiser initialized successfully.");
        } catch (Exception e) {
            log.error("[GTCRN_PROCESSOR] Failed to initialize sherpa-onnx OfflineSpeechDenoiser: {}", e.getMessage(), e);
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 关闭 sherpa-onnx 语音增强器。
     */
    public void destroy() {
        if (denoiser != null) {
            log.info("[GTCRN_PROCESSOR] Releasing sherpa-onnx OfflineSpeechDenoiser resources...");
            denoiser.release();
            denoiser = null;
            log.info("[GTCRN_PROCESSOR] sherpa-onnx OfflineSpeechDenoiser resources released.");
        }
    }

    /**
     * 处理音频块，进行降噪（通过 sherpa-onnx GTCRN 模型）。
     *
     * @return 降噪后的 16-bit PCM 字节数组。
     * @throws Exception 如果处理失败。
     */
    public ByteBuffer processAudioChunk(ByteBuffer pcm16ByteBuffer) throws Exception {
        if (pcm16ByteBuffer == null || !pcm16ByteBuffer.hasRemaining()) { return ByteBuffer.allocate(0); }
        byte[] pcm16Bytes = new byte[pcm16ByteBuffer.remaining()];
        pcm16ByteBuffer.get(pcm16Bytes);

        // 1. 将 16-bit PCM (byte[]) 转换为 float[]，并进行重采样到 GTCRN 期望的 16kHz 单声道
        // 注意：AudioUtils.convertPcm16ToFloatAndResample 已经将目标采样率固定为 16000 Hz
        float[] floatAudioSamples = AudioUtils.convertPcm16ToFloatAndResample(pcm16Bytes, inputSampleRate, inputChannels, GTCRN_SAMPLE_RATE);

        if (floatAudioSamples.length == 0) {
            return ByteBuffer.allocate(0);
        }

        log.info("[GTCRN_PROCESSOR] Input float audio samples length: {}", floatAudioSamples.length);

        ByteArrayOutputStream denoisedPcmOutputStream = new ByteArrayOutputStream();

        try {
            DenoisedAudio enhancedAudio = denoiser.run(floatAudioSamples, GTCRN_SAMPLE_RATE);
            float[] enhancedSamples = enhancedAudio.getSamples();

            log.info("[GTCRN_PROCESSOR] Enhanced float audio samples length: {}", enhancedSamples.length);
            log.info("[GTCRN_PROCESSOR] Enhanced audio sample rate: {} Hz", enhancedAudio.getSampleRate());

            if (enhancedSamples != null && enhancedSamples.length > 0) {
                byte[] denoisedPcmData = AudioUtils.convertFloatToPcm16(enhancedSamples);
                denoisedPcmOutputStream.write(denoisedPcmData);
                log.info("[GTCRN_PROCESSOR] Denoised PCM data written to output stream. Current stream size: {}", denoisedPcmOutputStream.size());
            } else {
                log.warn("[GTCRN_PROCESSOR] sherpa-onnx denoiser did not produce any enhanced samples. Skipping output.");
            }
        } catch (Exception e) {
            log.error("[GTCRN_PROCESSOR] Error during sherpa-onnx speech enhancement: {}", e.getMessage(), e);
            e.printStackTrace();
            throw e;
        }

        return ByteBuffer.wrap(denoisedPcmOutputStream.toByteArray());
    }
}
