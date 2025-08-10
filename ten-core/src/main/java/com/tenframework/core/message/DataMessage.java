package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 数据消息，对齐C/Python中的TEN_MSG_TYPE_DATA。
 *
 * C端结构体定义: core/include_internal/ten_runtime/msg/data/data.h (L18-23)
 * ```c
 * typedef struct ten_data_t {
 * ten_msg_t msg_hdr; // (基消息头，对应 Message 基类字段)
 * ten_signature_t signature; // (C 内部签名，无需 Java 映射)
 * ten_value_t data; // buf // (实际数据内容)
 * } ten_data_t;
 * ```
 *
 * Java 实现中，我们将 `data` 直接作为类的字段，并通过 `@JsonProperty` 进行映射。
 * 不再需要自定义的 Jackson `JsonSerializer` 和 `JsonDeserializer`。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class DataMessage extends Message {

    /**
     * 实际的数据内容。
     * 对应C端 `ten_data_t` 结构体中的 `data` 字段。
     * C类型: `ten_value_t` (内部为 `buf`，即字节缓冲区)
     */
    @JsonProperty("data")
    private byte[] data;

    /**
     * 全参构造函数，用于创建数据消息。
     *
     * @param id         消息ID。
     * @param srcLoc     源位置。
     * @param type       消息类型 (应为 DATA_MESSAGE)。
     * @param destLocs   目的位置。
     * @param properties 消息属性。
     * @param timestamp  消息时间戳。
     * @param data       实际数据内容。
     */
    public DataMessage(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp, byte[] data) {
        super(id, type, srcLoc, destLocs, null, properties, timestamp); // 传入 null 作为 name
        this.data = data;
    }

    /**
     * 用于内部创建的简化构造函数。
     *
     * @param id       消息ID。
     * @param type     消息类型 (应为 DATA_MESSAGE)。
     * @param srcLoc   源位置。
     * @param destLocs 目的位置。
     * @param data     实际数据内容。
     */
    public DataMessage(String id, MessageType type, Location srcLoc, List<Location> destLocs, byte[] data) {
        super(id, type, srcLoc, destLocs, null, Collections.emptyMap(), System.currentTimeMillis()); // 传入 null 作为 name
        this.data = data;
    }

    /**
     * 辅助构造函数，接受 String 类型的 payload，并将其转换为 byte[]。
     *
     * @param id       消息ID。
     * @param type     消息类型 (应为 DATA_MESSAGE)。
     * @param srcLoc   源位置。
     * @param destLocs 目的位置。
     * @param payload  字符串形式的有效载荷。
     */
    public DataMessage(String id, MessageType type, Location srcLoc, List<Location> destLocs, String payload) {
        this(id, type, srcLoc, destLocs, payload != null ? payload.getBytes(StandardCharsets.UTF_8) : null);
    }

    /**
     * 获取数据大小（字节数）。
     */
    public int getDataSize() {
        return data != null ? data.length : 0;
    }

    /**
     * 获取数据字节数组的拷贝。
     */
    public byte[] getDataBytes() {
        return data != null ? data.clone() : new byte[0]; // 使用 clone 进行深拷贝
    }
}