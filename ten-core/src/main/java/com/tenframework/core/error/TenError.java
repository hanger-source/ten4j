package com.tenframework.core.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 对齐C/Python中的ten_error_t结构体，用于封装错误码和错误消息。
 * 0L表示成功（TEN_ERROR_CODE_OK）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class TenError {

    @JsonProperty("code")
    private long code; // 对应C/Python的error_code (int64_t)

    @JsonProperty("message")
    private String message; // 对应C/Python的error_message

    /**
     * 判断错误是否表示成功。
     * 
     * @return 如果错误码为0L，则返回true，表示成功。
     */
    public boolean isSuccess() {
        return this.code == 0L;
    }

    /**
     * 创建一个表示成功的TenError实例。
     * 
     * @return 成功的TenError实例
     */
    public static TenError success() {
        return new TenError(0L, "OK");
    }

    /**
     * 创建一个表示失败的TenError实例。
     * 
     * @param code    错误码
     * @param message 错误消息
     * @return 失败的TenError实例
     */
    public static TenError failure(long code, String message) {
        return new TenError(code, message);
    }
}