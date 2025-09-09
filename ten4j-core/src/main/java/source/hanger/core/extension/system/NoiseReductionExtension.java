package source.hanger.core.extension.system;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.audio.GTCRNDenoiseProcessor; // 导入新模块的处理器
import source.hanger.core.util.ByteBufUtils;
import source.hanger.audio.DenoisedAudioFileWriter; // 导入新创建的音频文件写入器

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.FileNotFoundException;

@Slf4j
public class NoiseReductionExtension extends BaseExtension {

    private GTCRNDenoiseProcessor denoisingProcessor;
    private int inputSampleRate; // 期望的输入音频采样率 (来自客户端)
    private int inputChannels;   // 期望的输入音频声道数 (来自客户端)
    private String gtcrnModelResourcePath; // GTCRN ONNX 模型在 resources 中的路径
    private String outputDirectory; // 降噪音频文件输出目录
    private DenoisedAudioFileWriter denoisedAudioFileWriter; // 使用新的音频文件写入器
    private DenoisedAudioFileWriter originalAudioFileWriter; // 用于写入原始音频

    // 测试用文件输入
    private AudioInputStream testAudioInputStream;
    private byte[] testReadBuffer;
    private String testWavFilePath;

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        super.onExtensionConfigure(env, properties);

        this.inputSampleRate = env.getPropertyInt("input_sample_rate").orElse(16000);
        this.inputChannels = env.getPropertyInt("input_channels").orElse(1);
        this.gtcrnModelResourcePath = "gtcrn/gtcrn_simple.onnx"; // Fixed path as requested
        this.outputDirectory = env.getPropertyString("output_directory").orElse("denoised_audio_output");
        this.testWavFilePath = "ten4j/ten4j-audio-processing/samples_Samples1_Samples1_noisy.wav"; // 指定测试文件路径

        log.info("[{}] NoiseReductionExtension configured. Input Sample Rate: {}Hz, Channels: {}, GTCRN Model: {}, Output Dir: {}, Test WAV: {}",
                env.getExtensionName(), inputSampleRate, inputChannels, gtcrnModelResourcePath, outputDirectory, testWavFilePath);
    }

    @Override
    public void onInit(TenEnv env) {
        super.onInit(env);
        try {
            denoisingProcessor = new GTCRNDenoiseProcessor(gtcrnModelResourcePath, inputSampleRate, inputChannels);
            denoisingProcessor.init(); // 初始化 ONNX Runtime 和 GTCRN 模型
            log.info("[{}] GTCRNDenoiseProcessor initialized.", env.getExtensionName());

            // 准备输出目录
            File dir = new File(outputDirectory);
            if (!dir.exists()) {
                dir.mkdirs(); // 创建目录
            }
            // 初始化 DenoisedAudioFileWriter
            String baseFileName = env.getExtensionName() + "_denoised";
            denoisedAudioFileWriter = new DenoisedAudioFileWriter(
                    baseFileName, outputDirectory, GTCRNDenoiseProcessor.GTCRN_SAMPLE_RATE, 1, 2);

            // 初始化用于写入原始音频的 DenoisedAudioFileWriter
            String originalFileName = env.getExtensionName() + "_original";
            originalAudioFileWriter = new DenoisedAudioFileWriter(
                    originalFileName, outputDirectory, inputSampleRate, inputChannels, 2); // 2 bytes/frame for 16-bit PCM

            // --- 测试模式：从 WAV 文件读取音频 --- //
            // this.testWavFilePath = "ten4j/ten4j-audio-processing/samples_Samples1_Samples1_noisy.wav"; // 指定测试文件路径
            this.testWavFilePath = new File("/Users/fuhangbo/ten-realtime-chat/ten4j/ten4j-audio-processing/samples_Samples1_Samples1_noisy.wav").getAbsolutePath(); // 使用绝对路径
            log.info("[{}] Attempting to load test WAV file from absolute path: {}", env.getExtensionName(), testWavFilePath);
            File wavFile = new File(testWavFilePath);
            if (!wavFile.exists()) {
                log.error("[{}] Test WAV file not found: {}", env.getExtensionName(), testWavFilePath);
                throw new FileNotFoundException("Test WAV file not found: " + testWavFilePath);
            }
            testAudioInputStream = AudioSystem.getAudioInputStream(wavFile);
            // 确保 WAV 文件的格式与期望的输入格式匹配，或者进行适当的转换
            // 这里我们假设 WAV 文件已经是 16kHz, mono, 16-bit PCM，如果不是需要添加转换逻辑
            AudioFormat wavFormat = testAudioInputStream.getFormat();
            log.info("[{}] Test WAV file format: {}", env.getExtensionName(), wavFormat);
            if (wavFormat.getSampleRate() != inputSampleRate || wavFormat.getChannels() != inputChannels || wavFormat.getSampleSizeInBits() != 16) {
                log.warn("[{}] Test WAV file format ({}) does not match expected input format ({}Hz, {} channels, 16-bit). May cause issues.",
                         env.getExtensionName(), wavFormat, inputSampleRate, inputChannels);
                // 实际项目中这里可能需要 AudioSystem.getAudioInputStream(targetFormat, testAudioInputStream) 进行格式转换
            }
            // 使用一个合理的缓冲大小，例如 16kHz 100ms 的数据量 (16000 * 2 bytes/sample * 1 channel * 0.1s = 3200 bytes)
            testReadBuffer = new byte[inputSampleRate * inputChannels * 2 / 10]; // 100ms 的数据
            log.info("[{}] Initialized test WAV file input from: {}", env.getExtensionName(), testWavFilePath);

        } catch (Exception e) {
            log.error("[{}] Failed to initialize NoiseReductionExtension: {}", env.getExtensionName(), e.getMessage(), e);
            throw new RuntimeException("Failed to initialize NoiseReductionExtension", e);
        }
    }

    @Override
    public void onDestroy(TenEnv env) {
        super.onDestroy(env);
        if (denoisingProcessor != null) {
            try {
                denoisingProcessor.destroy(); // 释放 ONNX Runtime 资源
                log.info("[{}] GTCRNDenoiseProcessor destroyed.", env.getExtensionName());
            } catch (Exception e) {
                log.error("[{}] Error destroying GTCRNDenoiseProcessor: {}", env.getExtensionName(), e.getMessage(), e);
            }
        }
        if (denoisedAudioFileWriter != null) {
            denoisedAudioFileWriter.close();
            log.info("[{}] DenoisedAudioFileWriter closed and audio saved.", env.getExtensionName());
        }
        if (originalAudioFileWriter != null) {
            originalAudioFileWriter.close();
            log.info("[{}] OriginalAudioFileWriter closed and original audio saved.", env.getExtensionName());
        }
        if (testAudioInputStream != null) {
            try {
                testAudioInputStream.close();
                log.info("[{}] Test WAV AudioInputStream closed.", env.getExtensionName());
            } catch (IOException e) {
                log.error("[{}] Error closing test WAV AudioInputStream: {}", env.getExtensionName(), e.getMessage(), e);
            }
        }
        log.info("[{}] NoiseReductionExtension destroyed, resources released.", env.getExtensionName());
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrameMessage) {
        // 在测试模式下，忽略 audioFrameMessage，从 WAV 文件读取
        // ByteBuffer originalAudioDataBuffer = ByteBufUtils.toByteBuffer(audioFrameMessage.getBuf());

        ByteBuffer originalAudioDataBuffer = null;
        try {
            int bytesRead = testAudioInputStream.read(testReadBuffer);
            if (bytesRead != -1) {
                originalAudioDataBuffer = ByteBuffer.wrap(testReadBuffer, 0, bytesRead);
                log.info("[{}] Read {} bytes from test WAV file.", env.getExtensionName(), bytesRead);
            } else {
                log.info("[{}] End of test WAV file reached. Stopping processing.", env.getExtensionName());
                // 达到文件末尾，可以考虑停止 Extension 或通知上层系统
                return; // 不再处理
            }
        } catch (IOException e) {
            log.error("[{}] Error reading from test WAV file: {}", env.getExtensionName(), e.getMessage(), e);
            e.printStackTrace();
            return;
        }

        if (originalAudioDataBuffer == null || !originalAudioDataBuffer.hasRemaining()) {
            log.warn("[{}] Received empty audio frame (ByteBuffer), skipping denoising.", env.getExtensionName());
            return;
        }

        if (denoisingProcessor == null) {
            log.error("[{}] Denoising processor is not initialized. Skipping denoising.", env.getExtensionName());
            return;
        }

        try {
            ByteBuffer denoisedAudioDataBuffer = denoisingProcessor.processAudioChunk(originalAudioDataBuffer);
            if (denoisedAudioDataBuffer != null && denoisedAudioDataBuffer.hasRemaining()) {
                denoisedAudioFileWriter.writeAudioFrame(denoisedAudioDataBuffer); // 将 ByteBuffer 直接写入文件
                log.info("[{}] Denoised audio frame written. Remaining in buffer: {} bytes.",
                          env.getExtensionName(), denoisedAudioDataBuffer.remaining());
            } else {
                log.warn("[{}] Denoising processor did not produce enough data for a frame or returned empty buffer. Skipping output.", env.getExtensionName());
            }

        } catch (Exception e) {
            log.error("[{}] Error during GTCRN denoising: {}", env.getExtensionName(), e.getMessage(), e);
            e.printStackTrace(); // 添加堆栈跟踪
        }
    }
}
