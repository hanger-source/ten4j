package com.tenframework.core.message;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * 音频帧消息，对齐C/Python中的TEN_MSG_TYPE_AUDIO_FRAME。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h
 * (L19-58)
 * ```c
 * typedef struct ten_audio_frame_t {
 * ten_msg_t msg_hdr; // (基消息头，对应 Message 基类字段)
 * ten_signature_t signature; // (C 内部签名，无需 Java 映射)
 * ten_value_t timestamp; // int64. 音频帧时间戳
 * ten_value_t sample_rate; // int32. 采样率
 * ten_value_t bytes_per_sample; // int32. 每采样字节数
 * ten_value_t samples_per_channel; // int32. 每声道采样数
 * ten_value_t number_of_channel; // int32. 声道数
 * ten_value_t channel_layout; // uint64. 声道布局ID
 * ten_value_t data_fmt; // int32 (TEN_AUDIO_FRAME_DATA_FMT). 数据格式
 * ten_value_t buf; // buf. 实际音频帧数据
 * ten_value_t line_size; // int32. 行大小
 * ten_value_t is_eof; // bool. 是否为文件结束标记
 * } ten_audio_frame_t;
 * ```
 *
 * Java 实现中，我们将所有字段直接作为类的字段，并通过 `@JsonProperty` 进行映射。
 * 不再需要自定义的 Jackson `JsonSerializer` 和 `JsonDeserializer`。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
@Slf4j
public class AudioFrameMessage extends Message {

    /**
     * 音频帧时间戳 (内部时间戳)。
     * 对应C端 `ten_audio_frame_t` 结构体中的 `timestamp` 字段。
     * 注意：这与 `Message` 基类中的 `timestamp` (消息发送时间) 不同，此为帧本身的媒体时间戳。
     */
    @JsonProperty("timestamp")
    private long frameTimestamp; // 重命名以避免与基类timestamp混淆，且更符合其含义

    /**
     * 音频采样率（Hz）。
     */
    @JsonProperty("sample_rate")
    private int sampleRate;

    /**
     * 每采样字节数。
     */
    @JsonProperty("bytes_per_sample")
    private int bytesPerSample;

    /**
     * 每声道采样数。
     */
    @JsonProperty("samples_per_channel")
    private int samplesPerChannel;

    /**
     * 声道数。
     */
    @JsonProperty("number_of_channel")
    private int numberOfChannel;

    /**
     * 声道布局ID (FFmpeg)。
     */
    @JsonProperty("channel_layout")
    private long channelLayout;

    /**
     * 音频数据格式。
     */
    @JsonProperty("data_fmt")
    private int dataFormat;

    /**
     * 实际的音频帧数据（字节缓冲区）。
     */
    @JsonProperty("buf")
    private byte[] buf;

    /**
     * 音频数据行大小。
     */
    @JsonProperty("line_size")
    private int lineSize;

    /**
     * 是否为文件结束（EOF）标记。
     */
    @JsonProperty("is_eof")
    private boolean isEof;

    /**
     * 全参构造函数，用于创建音频帧消息。
     */
    public AudioFrameMessage(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp,
            long frameTimestamp, int sampleRate, int bytesPerSample, int samplesPerChannel, int numberOfChannel,
            long channelLayout, int dataFormat, byte[] buf, int lineSize, boolean isEof) {
        super(id, type, srcLoc, destLocs, null, properties, timestamp); // 传入 null 作为 name
        this.frameTimestamp = frameTimestamp;
        this.sampleRate = sampleRate;
        this.bytesPerSample = bytesPerSample;
        this.samplesPerChannel = samplesPerChannel;
        this.numberOfChannel = numberOfChannel;
        this.channelLayout = channelLayout;
        this.dataFormat = dataFormat;
        this.buf = buf;
        this.lineSize = lineSize;
        this.isEof = isEof;
    }

    /**
     * 简化构造函数，用于内部创建。
     */
    public AudioFrameMessage(String id, Location srcLoc, List<Location> destLocs, long frameTimestamp, int sampleRate,
            int bytesPerSample, int samplesPerChannel, int numberOfChannel,
            long channelLayout, int dataFormat, byte[] buf, int lineSize, boolean isEof) {
        super(id, MessageType.AUDIO_FRAME, srcLoc, destLocs, null, Collections.emptyMap(), System.currentTimeMillis()); // 传入
                                                                                                                        // null
                                                                                                                        // 作为
                                                                                                                        // name
        this.frameTimestamp = frameTimestamp;
        this.sampleRate = sampleRate;
        this.bytesPerSample = bytesPerSample;
        this.samplesPerChannel = samplesPerChannel;
        this.numberOfChannel = numberOfChannel;
        this.channelLayout = channelLayout;
        this.dataFormat = dataFormat;
        this.buf = buf;
        this.lineSize = lineSize;
        this.isEof = isEof;
    }

    /**
     * 创建静音音频帧。
     *
     * @param id             消息ID
     * @param srcLoc         源位置
     * @param timestamp      消息时间戳
     * @param durationMs     持续时长（毫秒）
     * @param sampleRate     采样率
     * @param channels       声道数
     * @param bytesPerSample 每采样字节数
     * @return 静音音频帧实例
     */
    public static AudioFrameMessage silence(String id, Location srcLoc, long timestamp, int durationMs, int sampleRate,
        int channels, int bytesPerSample) {
        int samplesPerChannel = (durationMs * sampleRate) / 1000;
        int totalSamples = samplesPerChannel * channels;
        byte[] silenceData = new byte[totalSamples * bytesPerSample];
        // 默认为0，表示静音

        return new AudioFrameMessage(id, srcLoc, MessageType.AUDIO_FRAME, Collections.emptyList(),
            Map.of(), timestamp, // properties, timestamp
            0L, sampleRate, bytesPerSample, samplesPerChannel, channels,
            0L, 0, // channelLayout, dataFormat 默认值
            silenceData, 0, false); // buf, lineSize, isEof 默认值
    }

    /**
     * 创建EOF标记音频帧。
     *
     * @param id        消息ID
     * @param srcLoc    源位置
     * @param timestamp 消息时间戳
     * @return EOF音频帧实例
     */
    public static AudioFrameMessage eof(String id, Location srcLoc, long timestamp) {
        return new AudioFrameMessage(id, srcLoc, MessageType.AUDIO_FRAME, Collections.emptyList(),
            Map.of(), timestamp, // properties, timestamp
            0L, 0, 0, 0, 0, // frameTimestamp, sampleRate, bytesPerSample, samplesPerChannel,
            // numberOfChannel
            0L, 0, // channelLayout, dataFormat
            new byte[0], 0, true); // buf, lineSize, isEof
    }

    /**
     * 获取音频数据大小（字节数）。
     */
    public int getDataSize() {
        return buf != null ? buf.length : 0;
    }

    /**
     * 获取音频数据的字节数组拷贝。
     */
    public byte[] getDataBytes() {
        return buf != null ? buf.clone() : new byte[0]; // 使用 clone 进行深拷贝
    }

    /**
     * 设置音频数据（字节数组）。
     */
    public void setDataBytes(byte[] bytes) {
        buf = bytes;
    }

    /**
     * 计算每声道采样数。
     * 注意：此方法依赖于 `bytesPerSample` 和 `numberOfChannel`，如果这两个字段未正确设置，结果可能不准确。
     */
    public int calculateSamplesPerChannel() {
        if (buf == null || buf.length == 0 || numberOfChannel <= 0 || bytesPerSample <= 0) {
            return 0;
        }
        int totalBytes = buf.length;
        int totalSamples = totalBytes / bytesPerSample; // 总采样点数
        return totalSamples / numberOfChannel; // 每声道采样数
    }

    /**
     * 获取音频时长（毫秒）。
     */
    public double getDurationMs() {
        int calculatedSamplesPerChannel = calculateSamplesPerChannel(); // 使用计算的每声道采样数
        if (calculatedSamplesPerChannel <= 0 || sampleRate <= 0) {
            return 0.0;
        }
        return (double) calculatedSamplesPerChannel * 1000.0 / sampleRate;
    }

    /**
     * 获取每秒字节数（比特率）。
     */
    public int getBytesPerSecond() {
        if (sampleRate <= 0 || numberOfChannel <= 0 || bytesPerSample <= 0) {
            return 0;
        }
        return sampleRate * numberOfChannel * bytesPerSample;
    }

    /**
     * 检查是否有音频数据。
     */
    public boolean hasData() {
        return buf != null && buf.length > 0;
    }

    /**
     * 检查是否为空音频帧。
     */
    public boolean isEmpty() {
        return !hasData();
    }

    public boolean checkIntegrity() { // 移除 @Override
        // 假设Message基类有一个checkIntegrity方法，或者在这里实现完整逻辑
        return getId() != null && !getId().isEmpty() &&
                validateAudioParameters();
    }

    /**
     * 验证音频参数。
     */
    private boolean validateAudioParameters() {
        return buf != null && buf.length >= 0 &&
                sampleRate > 0 &&
                numberOfChannel > 0 &&
                bytesPerSample > 0;
    }

    @Override
    public AudioFrameMessage clone() {
        // 实现深拷贝
        return new AudioFrameMessage(getId(), getSrcLoc(), getDestLocs(),
            frameTimestamp, sampleRate, bytesPerSample,
            samplesPerChannel, numberOfChannel, channelLayout,
            dataFormat, buf != null ? buf.clone() : null, lineSize, isEof);
    }
}