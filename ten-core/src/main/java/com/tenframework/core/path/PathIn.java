package com.tenframework.core.path;

import com.tenframework.core.message.Location;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * `PathIn` 代表一个命令的入站路径，用于追踪命令的来源和上下文。
 * 对应C语言中的 `ten_path_in_t` 结构体。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PathIn extends Path {

    // PathIn 仅作为 Path 的一个类型化子类，不包含额外字段

    /**
     * 构造函数，用于创建 PathIn 实例。
     *
     * @param commandName     命令名称
     * @param commandId       命令ID
     * @param parentCommandId 父命令ID
     * @param sourceLocation  命令源位置
     */
    public PathIn(String commandName, String commandId, String parentCommandId, Location sourceLocation) {
        super(commandName, commandId, parentCommandId, sourceLocation);
    }
}