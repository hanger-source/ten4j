package source.hanger.audio;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.math3.complex.Complex; // 导入 Commons Math 的 Complex 类
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;


/**
 * 音频处理工具类，用于重采样和格式转换。
 * 注意：javax.sound.sampled 在后端服务环境中可能需要额外的配置或库支持，
 * 或者考虑使用更轻量级的音频处理库进行重采样。
 */
public class AudioUtils {

    private static final Logger log = LoggerFactory.getLogger(AudioUtils.class);

    /**
     * 将 16-bit PCM (byte[]) 转换为 float[]，并进行重采样到目标采样率和单声道。
     *
     * @param pcmData      原始 16-bit PCM 字节数组
     * @param inSampleRate 输入采样率
     * @param inChannels   输入声道数
     * @param outSampleRate 目标采样率
     * @return 转换并重采样后的 float[] 数组 (单声道)
     * @throws IOException 如果音频处理失败
     */
    public static float[] convertPcm16ToFloatAndResample(byte[] pcmData, int inSampleRate, int inChannels, int outSampleRate) throws IOException {
        if (pcmData == null || pcmData.length == 0) {
            return new float[0];
        }

        // 目标格式：目标采样率, 16-bit PCM, 单声道
        AudioFormat targetFormat = new AudioFormat(16000, 16, 1, true, false); // 固定为 16000 Hz

        // 如果输入格式已经与目标格式匹配，则直接进行转换，无需 AudioSystem 重采样
        if (inSampleRate == targetFormat.getSampleRate() && inChannels == targetFormat.getChannels()) {
            log.info("[%s] Input audio format already matches target format (16kHz, mono). Skipping AudioSystem resampling.", "AUDIO_UTILS");
            float[] floatData = new float[pcmData.length / 2];
            ShortBuffer shortBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            for (int i = 0; i < floatData.length; i++) {
                floatData[i] = (float) shortBuffer.get(i) / 32768.0f; // 归一化到 [-1, 1]
            }
            return floatData;
        }

        AudioFormat originalFormat = new AudioFormat(inSampleRate, 16, inChannels, true, false); // signed, little-endian
        ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
        AudioInputStream sourceStream = new AudioInputStream(bais, originalFormat, pcmData.length / originalFormat.getFrameSize());

        AudioInputStream resampledStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
        if (resampledStream == null || !resampledStream.getFormat().equals(targetFormat)) {
            log.warn("AudioSystem does not support direct resampling from {} to {}. Attempting manual conversion (might be less efficient).", originalFormat, targetFormat);
            // Fallback: 可以考虑手动实现重采样或使用第三方库
            // 为了简化，这里仍然抛出异常，如果实际需要更复杂的 fallback，可以根据需求添加
            throw new IOException("AudioSystem does not support direct resampling for required format.");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = resampledStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        byte[] resampledPcmData = baos.toByteArray();

        float[] floatData = new float[resampledPcmData.length / 2];
        ShortBuffer shortBuffer = ByteBuffer.wrap(resampledPcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        for (int i = 0; i < floatData.length; i++) {
            floatData[i] = (float) shortBuffer.get(i) / 32768.0f; // 归一化到 [-1, 1]
        }
        return floatData;
    }

    /**
     * 将 float[] 样本转换为 16-bit PCM (byte[])。
     *
     * @param floatData float[] 样本数据
     * @return 16-bit PCM 字节数组
     */
    public static byte[] convertFloatToPcm16(float[] floatData) {
        if (floatData == null || floatData.length == 0) {
            return new byte[0];
        }

        byte[] pcmData = new byte[floatData.length * 2]; // 16-bit PCM，每个样本 2 字节
        ByteBuffer byteBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer shortBuffer = byteBuffer.asShortBuffer();

        for (float sample : floatData) {
            // 裁剪超出范围的值，避免溢出
            float clipped = Math.max(-1.0f, Math.min(1.0f, sample));
            shortBuffer.put((short) (clipped * 32767.0f)); // 反归一化到 short 范围
        }
        return pcmData;
    }

    /**
     * 生成 Hann 窗函数。
     *
     * @param windowSize 窗口大小。
     * @return 包含 Hann 窗函数值的 float 数组。
     */
    public static float[] hannWindow(int windowSize) {
        float[] window = new float[windowSize];
        for (int i = 0; i < windowSize; i++) {
            window[i] = (float) (0.5f - 0.5f * Math.cos(2 * Math.PI * i / (windowSize - 1)));
        }
        return window;
    }

    /**
     * 执行短时傅里叶变换 (STFT)，将时域音频数据转换为频域特征。
     *
     * @param audioFrame 预处理后的时域音频帧 (float[])。
     * @param windowSize 窗函数大小。
     * @param hopLength 帧移（相邻帧之间的样本数）。
     * @param n_fft FFT 变换的长度。
     * @return 4 维浮点数组，形状为 [1, n_fft/2 + 1, 1, 2] (批次大小, 频率 bin, 时间步, 实部/虚部)。
     */
    public static float[][][][] stft(float[] audioFrame, int windowSize, int hopLength, int n_fft) {
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

        try {
            // 计算分帧数量 (此处简化为一帧，因为模型期望 [1, 257, 1, 2] 的时间步为 1)
            int numFrames = (audioFrame.length - windowSize) / hopLength + 1;
            if (numFrames <= 0) { // 如果音频帧太短，不足以形成一帧
                log.warn("[GTCRN_PROCESSOR] [STFT_DEBUG] Audio frame too short for STFT. Frame length: {}, Window size: {}, Hop length: {}",
                         audioFrame.length, windowSize, hopLength);
                // 返回一个空的或者全零的张量，以避免后续错误
                return new float[1][n_fft / 2 + 1][1][2];
            }

            float[] window = hannWindow(windowSize);

            // 只处理第一帧 (或唯一的帧)，因为模型期望 time_steps 为 1
            float[] frame = new float[windowSize];
            System.arraycopy(audioFrame, 0, frame, 0, windowSize);

            // 应用窗函数
            for (int i = 0; i < windowSize; i++) {
                frame[i] *= window[i];
            }

            // 将实数帧转换为 Complex 数组，进行 FFT 变换
            Complex[] complexFrame = new Complex[n_fft];
            for (int i = 0; i < n_fft; i++) {
                if (i < windowSize) {
                    complexFrame[i] = new Complex(frame[i], 0.0);
                } else {
                    complexFrame[i] = Complex.ZERO;
                }
            }

            Complex[] fftResult = transformer.transform(complexFrame, TransformType.FORWARD);

            int numFrequencyBins = n_fft / 2 + 1; // 257
            float[][][][] stftOutput = new float[1][numFrequencyBins][1][2];

            for (int i = 0; i < numFrequencyBins; i++) {
                stftOutput[0][i][0][0] = (float) fftResult[i].getReal(); // 实部
                stftOutput[0][i][0][1] = (float) fftResult[i].getImaginary(); // 虚部
            }
            return stftOutput;
        } catch (Exception e) {
            log.error("[GTCRN_PROCESSOR] [STFT_DEBUG] Error in stft method: {}", e.getMessage(), e);
            e.printStackTrace();
            throw e; // Re-throw to propagate the error
        }
    }

    /**
     * 执行逆短时傅里叶变换 (ISTFT)，将频域特征转换回时域音频。
     *
     * @param stftFeatures 4 维频域特征数组，形状为 [1, n_fft/2 + 1, 1, 2]。
     * @param windowSize 窗函数大小。
     * @param hopLength 帧移（相邻帧之间的样本数）。
     * @param n_fft FFT 变换的长度。
     * @return 时域音频数据 (float[])。
     */
    public static float[] istft(float[][][][] stftFeatures, int windowSize, int hopLength, int n_fft) {
        if (stftFeatures == null || stftFeatures.length == 0 || stftFeatures[0].length == 0) {
            log.warn("STFT features are empty for ISTFT. Returning empty time domain frame.");
            return new float[0];
        }
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

        int numFrequencyBins = n_fft / 2 + 1; // 257

        // 假设只有一个时间步 (因为模型的输出形状是 [1, 257, 1, 2])
        // 因此我们只需要处理一帧
        Complex[] complexFrame = new Complex[n_fft];
        for (int i = 0; i < numFrequencyBins; i++) {
            double real = stftFeatures[0][i][0][0];
            double imag = stftFeatures[0][i][0][1];
            complexFrame[i] = new Complex(real, imag);

            // 填充对称部分 (除了 DC 和 Nyquist 频率)
            if (i > 0 && i < n_fft / 2) {
                complexFrame[n_fft - i] = new Complex(real, -imag); // 共轭对称
            }
        }

        // 处理 Nyquist 频率 (如果存在)
        if (n_fft % 2 == 0) { // 偶数 FFT 大小有 Nyquist 频率
            double real = stftFeatures[0][n_fft / 2][0][0];
            double imag = stftFeatures[0][n_fft / 2][0][1];
            complexFrame[n_fft / 2] = new Complex(real, imag); // Nyquist 频率的虚部通常为 0
        }

        Complex[] ifftResult = transformer.transform(complexFrame, TransformType.INVERSE);

        float[] timeDomainFrame = new float[windowSize];
        for (int i = 0; i < windowSize; i++) {
            timeDomainFrame[i] = (float) ifftResult[i].getReal();
        }

        // 应用 Hann 窗函数的逆操作 (通常在重叠相加时处理，这里简化为直接应用)
        float[] window = hannWindow(windowSize);
        for (int i = 0; i < windowSize; i++) {
            // 避免除以零
            if (window[i] != 0) {
                timeDomainFrame[i] /= window[i];
            }
        }

        // 由于模型只处理一个时间步，并且我们是单帧处理，这里不需要复杂的重叠相加
        // 返回处理后的时域帧
        return timeDomainFrame;
    }
}
