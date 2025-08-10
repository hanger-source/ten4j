package source.hanger.server;

import java.util.concurrent.TimeUnit;

import source.hanger.core.app.App;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServerMain {

    private static final int DEFAULT_PORT = 9090;
    private static final String DEFAULT_APP_URI = "ten://localhost/default_app";
    private static final String DEFAULT_CONFIG_PATH = "property.json"; // 假设配置文件路径

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        String appUri = DEFAULT_APP_URI;
        String configPath = DEFAULT_CONFIG_PATH;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                log.warn("无效的端口号参数: {}. 使用默认端口: {}", args[0], DEFAULT_PORT);
            }
        }
        if (args.length > 1) {
            appUri = args[1];
        }
        if (args.length > 2) {
            configPath = args[2];
        }

        log.info("ServerMain 启动中，端口: {}, App URI: {}, 配置路径: {}", port, appUri, configPath);

        // 1. 初始化 App 实例
        App app = new App(appUri, true, configPath); // true 表示每个 Engine 都有自己的 Runloop
        app.start(); // 启动 App

        // 2. 初始化 TenServer 实例，并将 App 传递给它
        TenServer tenServer = new TenServer(port, app);

        try {
            // 3. 启动 TenServer
            tenServer.start().get(10, TimeUnit.SECONDS); // 阻塞直到服务器启动完成

            log.info("TenServer 已在端口 {} 启动，等待连接...", tenServer.getPort());

            // 4. 注册一个关闭钩子，确保 App 和 TenServer 资源在 JVM 关闭时被清理
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("JVM 关闭钩子触发，正在停止 App 和 TenServer...");
                try {
                    tenServer.shutdown().get(10, TimeUnit.SECONDS); // 停止 TenServer
                } catch (Exception e) {
                    log.error("停止 TenServer 过程中发生异常: {}", e.getMessage(), e);
                } finally {
                    try {
                        app.stop(); // 停止 App
                    } catch (Exception e) {
                        log.error("停止 App 过程中发生异常: {}", e.getMessage(), e);
                    }
                    log.info("App 和 TenServer 已优雅关闭。");
                }
            }));

            // 这里不需要 f.channel().closeFuture().sync();
            // 因为 TenServer 内部已经处理了 channel 的生命周期，并且 ServerMain 只需要在启动后保持运行即可
            // 如果需要保持主线程活跃，可以添加一个阻塞调用，或者依赖后台线程
            // 在这里，主线程可以简单地退出，因为 shutdown hook 会处理清理

        } catch (Exception e) {
            log.error("TenServer 启动失败: {}", e.getMessage(), e);
            // 启动失败时，确保 App 也被停止
            try {
                app.stop();
            } catch (Exception appStopEx) {
                log.error("App 停止失败: {}", appStopEx.getMessage(), appStopEx);
            }
            throw e; // 重新抛出异常
        }
        log.info("ServerMain 线程结束。"); // 主线程任务完成，可以退出
    }
}