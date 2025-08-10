package com.tenframework.core.path;

import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.Location;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * `Path` 是 Ten 框架中命令和数据流路径的基类。
 * 对应C语言中的 `ten_path_t` 结构体，用于统一管理路径的通用属性。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Path {

    // 对应 C 端 `cmd_name`
    private String commandName;

    // 对应 C 端 `cmd_id`
    private String commandId; // 使用 String 类型以对齐 C 端的 ten_string_t

    // 对应 C 端 `parent_cmd_id`
    private String parentCommandId; // 使用 String 类型以对齐 C 端的 ten_string_t

    // 对应 C 端 `src_loc`
    private Location sourceLocation;

    // 对应 C 端 `cached_cmd_result`
    private CommandResult cachedCommandResult;

    // 对应 C 端 `has_received_final_cmd_result`
    private boolean hasReceivedFinalCommandResult;

    // 对应 C 端 `result_conversion` (暂时简化为不需要单独的 Java 类，如果复杂再引入)
    // private Object resultConversion;

    // 对应 C 端 `expired_time_us`
    private long expiredTimeUs;

    // 对应 C 端 `TEN_PATH_TYPE` (入站/出站)，可由子类确定或在此处通过枚举定义
     private PathType type;

    // 构造函数，用于初始化通用属性
    public Path(String commandName, String commandId, String parentCommandId, Location sourceLocation) {
        this.commandName = commandName;
        this.commandId = commandId;
        this.parentCommandId = parentCommandId;
        this.sourceLocation = sourceLocation;
        cachedCommandResult = null;
        hasReceivedFinalCommandResult = false;
        expiredTimeUs = 0; // 默认值
    }
}