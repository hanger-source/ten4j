package com.tenframework.core.path;

import java.util.concurrent.CompletableFuture;
import com.tenframework.core.message.CommandResult; // 确保导入 CommandResult
import com.tenframework.core.message.Location; // 确保导入 Location
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * `PathOut` 代表一个命令的出站路径，用于追踪命令的执行并回溯其结果。
 * 对应C语言中的 `ten_path_out_t` 结构体。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PathOut extends Path { // 继承 Path 基类

    private CompletableFuture<Object> resultFuture; // 用于完成命令结果的 CompletableFuture
    private ResultReturnPolicy returnPolicy; // 结果返回策略

    /**
     * 构造函数，用于创建 PathOut 实例。
     *
     * @param commandId           命令ID
     * @param parentCommandId     父命令ID
     * @param commandName         命令名称
     * @param sourceLocation      命令源位置
     * @param destinationLocation 命令目标位置 (Java 侧独有，用于路由)
     * @param resultFuture        用于完成命令结果的 CompletableFuture
     * @param returnPolicy        结果返回策略
     * @param returnLocation      命令结果回传到的目标位置
     */
    public PathOut(String commandId, String parentCommandId, String commandName, Location sourceLocation,
            Location destinationLocation, CompletableFuture<Object> resultFuture,
            ResultReturnPolicy returnPolicy, Location returnLocation) {
        // 调用父类构造函数初始化通用属性
        super(commandName, commandId, parentCommandId, sourceLocation);
        this.resultFuture = resultFuture;
        this.returnPolicy = returnPolicy;
        // destinationLocation 和 returnLocation 将作为 PathOut 独有的字段
        // 或者根据需要，将 destinationLocation 移到 Path 基类或 Message 中
        // 这里暂时将 returnLocation 作为 PathOut 的一个独有属性，不将其视为基类的 srcLoc 或 destLocs
        this.destinationLocation = destinationLocation; // 保留 destinationLocation，因为它在 C PathOut create 中有对应概念
        this.returnLocation = returnLocation; // 保留 returnLocation，因为它在 createOutPath 中使用
    }

    // 对应 C 端 ten_loc_t dest_loc; 但在 PathOut create 签名中是 destinationLocation
    // 将其作为 PathOut 独有属性，因为 Path 基类的 src_loc 是原始命令的源。
    private Location destinationLocation; // 命令的目标位置，在 C 端 create 签名中有对应

    // 对应 C 端 result_handler 的逻辑回传位置
    private Location returnLocation; // 命令结果回传到的目标位置
}