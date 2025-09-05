package source.hanger.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.buffer.ByteBuf;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import static source.hanger.core.message.MessageType.*;

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
@Slf4j
@SuperBuilder(toBuilder = true)
@Getter
public class VideoFrameMessage extends Message {

    /**
     * 像素格式。
     * 对应C端 `ten_video_frame_t` 结构体中的 `pixel_fmt` 字段。
     */
    @JsonProperty("pixel_fmt")
    private Integer pixelFormat;

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
    private ByteBuf data;


    /**
     * 获取视频数据大小（字节数）。
     */
    public int getDataSize() {
        //return data != null ? data.length : 0;
        //TODO
        return 0;
    }

    /**
     * 获取视频分辨率字符串，例如 "1920x1080"。
     */
    public String getResolution() {
        return "%dx%d".formatted(width, height);
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

    @Override
    public MessageType getType() {
        return VIDEO_FRAME;
    }
}