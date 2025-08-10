package com.tenframework.core.command.app;

import com.tenframework.core.connection.Connection;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.tenenv.TenEnvProxy;
import com.tenframework.core.app.AppEnvImpl;

/**
 * `AppCommandHandler` 接口定义了处理 App 级别命令的契约。
 * 实现此接口的类将负责处理特定类型的 App 级命令，例如 StartGraphCommand、StopGraphCommand 等。
 */
public interface AppCommandHandler {

    /**
     * 处理 App 级别的命令。
     *
     * @param appEnvProxy App 的 TenEnvProxy 实例。
     * @param command     要处理的命令。
     * @param connection  命令来源的连接，可能为 null (例如来自内部命令或自动启动)。
     * @return 命令处理的结果，通常是一个 CompletableFuture<Object> 或者 null。
     */
    Object handle(TenEnvProxy<AppEnvImpl> appEnvProxy, Command command, Connection connection);
}