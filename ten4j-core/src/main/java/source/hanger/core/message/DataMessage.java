package source.hanger.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

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

    public static DataMessage create(String name) {
        return (DataMessage)new DataMessage()
            .setType(MessageType.DATA)
            .setName(name)
            .setTimestamp(System.currentTimeMillis());
    }
}