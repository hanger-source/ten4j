package source.hanger.core.path;

import java.util.concurrent.CompletableFuture;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.Location;
import source.hanger.core.message.Message; // 导入 Message 类
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
    private Message originalCommand; // <-- 对应 C 语言的 ten_shared_ptr_t original_msg_ref

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
     * @param originalCommand     原始入站命令
     */
    public PathOut(String commandId, String parentCommandId, String commandName, Location sourceLocation,
            Location destinationLocation, CompletableFuture<Object> resultFuture,
            ResultReturnPolicy returnPolicy, Location returnLocation, Message originalCommand) {
        // 调用父类构造函数初始化通用属性
        super(commandName, commandId, parentCommandId, sourceLocation);
        this.resultFuture = resultFuture;
        this.returnPolicy = returnPolicy;
        this.destinationLocation = destinationLocation;
        this.returnLocation = returnLocation;
        this.originalCommand = originalCommand;
    }

    private Location destinationLocation;
    private Location returnLocation;
}