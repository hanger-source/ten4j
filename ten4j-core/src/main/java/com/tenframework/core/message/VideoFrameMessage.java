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

import static com.tenframework.core.message.MessageType.VIDEO_FRAME;

/**
 * 视频帧消息，对齐C/Python中的TEN_MSG_TYPE_VIDEO_FRAME。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/video_frame/video_frame.h
 * (L21-31)
 * ```c
 * typedef struct ten_video_frame_t {
 * ten_msg_t msg_hdr; // (基消息头，对应 Message 基类字段)
 * ten_signature_t signature; // (C 内部签名，无需 Java 映射)
 * ten_value_t pixel_fmt; // int32 (TEN_PIXEL_FMT) // 像素格式
 * ten_value_t timestamp; // int64 // 视频帧时间戳
 * ten_value_t width; // int32 // 宽度
 * ten_value_t height; // int32 // 高度
 * ten_value_t is_eof; // bool // 是否为文件结束标记
 * ten_value_t data; // buf // 实际视频帧数据
 * } ten_video_frame_t;
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
public class VideoFrameMessage extends Message {

    /**
     * 像素格式。
     * 对应C端 `ten_video_frame_t` 结构体中的 `pixel_fmt` 字段。
     */
    @JsonProperty("pixel_fmt")
    private int pixelFormat;

    /**
     * 视频帧时间戳 (内部时间戳)。
     * 对应C端 `ten_video_frame_t` 结构体中的 `timestamp` 字段。
     * 注意：这与 `Message` 基类中的 `timestamp` (消息发送时间) 不同，此为帧本身的媒体时间戳。
     */
    @JsonProperty("timestamp")
    private long frameTimestamp;

    /**
     * 视频帧宽度。
     */
    @JsonProperty("width")
    private int width;

    /**
     * 视频帧高度。
     */
    @JsonProperty("height")
    private int height;

    /**
     * 是否为文件结束（EOF）标记。
     */
    @JsonProperty("is_eof")
    private boolean isEof;

    /**
     * 实际的视频帧数据（字节缓冲区）。
     */
    @JsonProperty("data")
    private byte[] data;

    /**
     * 全参构造函数，用于创建视频帧消息。
     */
    public VideoFrameMessage(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp,
            int pixelFormat, long frameTimestamp, int width, int height, boolean isEof, byte[] data) {
        super(id, type, srcLoc, destLocs, null, properties, timestamp); // 传入 null 作为 name
        this.pixelFormat = pixelFormat;
        this.frameTimestamp = frameTimestamp;
        this.width = width;
        this.height = height;
        this.isEof = isEof;
        this.data = data;
    }

    /**
     * 简化构造函数，用于内部创建。
     */
    public VideoFrameMessage(String id, Location srcLoc, List<Location> destLocs,
            int pixelFormat, long frameTimestamp, int width, int height, boolean isEof, byte[] data) {
        super(id, VIDEO_FRAME, srcLoc, destLocs, null, Collections.emptyMap(), System.currentTimeMillis()); // 传入
                                                                                                                        // null
                                                                                                                        // 作为
                                                                                                                        // name
        this.pixelFormat = pixelFormat;
        this.frameTimestamp = frameTimestamp;
        this.width = width;
        this.height = height;
        this.isEof = isEof;
        this.data = data;
    }

    /**
     * 创建黑色视频帧。
     *
     * @param id          消息ID
     * @param srcLoc      源位置
     * @param timestamp   消息时间戳
     * @param width       宽度
     * @param height      高度
     * @param pixelFormat 像素格式
     * @return 黑色视频帧实例
     */
    public static VideoFrameMessage black(String id, Location srcLoc, long timestamp, int width, int height,
        int pixelFormat) {
        VideoFrameMessage frame = new VideoFrameMessage(id, srcLoc, VIDEO_FRAME, Collections.emptyList(),
            Map.of(), timestamp, // properties, timestamp
            pixelFormat, 0L, width, height, false, null); // frameTimestamp 默认 0L, data为null

        // 创建黑色帧数据（全0）
        int size = frame.getUncompressedSize();
        byte[] blackData = new byte[size];
        frame.setData(blackData); // 使用setData设置内部buf

        return frame;
    }

    /**
     * 创建EOF标记视频帧。
     *
     * @param id        消息ID
     * @param srcLoc    源位置
     * @param timestamp 消息时间戳
     * @return EOF视频帧实例
     */
    public static VideoFrameMessage eof(String id, Location srcLoc, long timestamp) {
        return new VideoFrameMessage(id, srcLoc, VIDEO_FRAME, Collections.emptyList(),
            Map.of(), timestamp, // properties, timestamp
            0, 0L, 0, 0, true, new byte[0]); // pixelFormat, frameTimestamp, width, height, isEof, data
    }

    /**
     * 获取视频数据大小（字节数）。
     */
    public int getDataSize() {
        return data != null ? data.length : 0;
    }

    /**
     * 获取视频数据的字节数组拷贝。
     */
    public byte[] getDataBytes() {
        return data != null ? data.clone() : new byte[0]; // 使用 clone 进行深拷贝
    }

    /**
     * 设置视频数据（字节数组）。
     */
    public void setDataBytes(byte[] bytes) {
        data = bytes;
    }

    /**
     * 获取视频分辨率字符串，例如 "1920x1080"。
     */
    public String getResolution() {
        return width + "x" + height;
    }

    /**
     * 获取宽高比。
     */
    public double getAspectRatio() {
        return height > 0 ? (double) width / height : 0.0;
    }

    /**
     * 计算理论上的未压缩帧大小（字节）。
     * 此方法基于像素格式和宽高估算，具体实现可能需要更详细的像素格式枚举及对应的字节计算规则。
     */
    public int getUncompressedSize() {
        if (width <= 0 || height <= 0) {
            return 0;
        }

        // TODO: 这里需要根据 TEN_PIXEL_FMT 枚举值来精确计算。目前使用示例性的硬编码。
        // 默认使用一个常见格式的计算，例如 YUV420P
        return width * height * 3 / 2; // 这是一个简化估算，实际取决于 pixelFormat
    }

    /**
     * 获取压缩比。
     */
    public double getCompressionRatio() {
        int uncompressedSize = getUncompressedSize();
        int compressedSize = getDataSize();
        return uncompressedSize > 0 && compressedSize > 0 ? (double) uncompressedSize / compressedSize : 1.0;
    }

    /**
     * 检查是否有视频数据。
     */
    public boolean hasData() {
        return data != null && data.length > 0;
    }

    /**
     * 检查是否为空视频帧。
     */
    public boolean isEmpty() {
        return !hasData();
    }

    public boolean checkIntegrity() { // 移除 @Override
        return getId() != null && !getId().isEmpty() &&
                width >= 0 && height >= 0; // 宽度和高度必须非负
    }

    @Override
    public VideoFrameMessage clone() {
        // 实现深拷贝
        return new VideoFrameMessage(getId(), getSrcLoc(), getDestLocs(),
            pixelFormat, frameTimestamp, width, height, isEof, getDataBytes());
    }
}