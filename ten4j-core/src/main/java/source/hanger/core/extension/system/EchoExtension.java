package source.hanger.core.extension.system;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.BaseExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

/**
 * EchoExtension - 简单的回显扩展
 * 用于验证端到端消息流和生命周期
 *
 * 功能：
 * 1. 接收Command并返回回显结果
 * 2. 接收Data并回显数据内容
 * 3. 接收音视频帧并记录信息
 * 4. 演示虚拟线程的使用
 * 5. 验证生命周期调用
 */
@Slf4j
public class EchoExtension extends BaseExtension { // Extend BaseExtension

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 移除重复的字段定义
    // private String extensionName;
    // private String appUri; // 新增字段来存储appUri
    private boolean isRunning = false;
    private long messageCount = 0;
    // 新增：保留 TenEnv 引用，方便其他方法使用
    // private TenEnvProxy<Extension> envProxy; // Replaced by TenEnv env in
    // BaseExtension
    // 移除 engine 和 extensionContext 字段

    // 构造函数
    public EchoExtension() {
        // 构造函数不再需要 extensionName 和 appUri 参数，因为它们会从 env 中获取
    }

    // Removed init method, handled by BaseExtension
    // @Override
    // public void init(String extensionName, Map<String, Object> properties, TenEnv
    // env) { // 修改签名
    // super.init(extensionName, properties, env); // Call super method
    // this.extensionName = extensionName; // Re-assign for consistency if needed
    // // this.env = env; // Set in BaseExtension
    // log.info("EchoExtension {} initialized.", extensionName);
    // }

    // Removed destroy method, handled by BaseExtension
    // @Override
    // public void destroy(TenEnv env) {
    // super.destroy(env); // Call super method
    // log.info("EchoExtension {} destroyed.", getExtensionName());
    // }

    // Removed getExtensionName and getAppUri, handled by BaseExtension
    // @Override
    // public String getExtensionName() {
    // // 从 envProxy 中获取 extensionName，确保一致性
    // return env != null ? env.getExtensionName() : null;
    // }

    // @Override
    // public String getAppUri() {
    // // 从 envProxy 中获取 appUri，确保一致性
    // return env != null ? env.getAppUri() : null;
    // }

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) {
        super.onConfigure(env, properties); // Call super method
        // this.env = env; // No longer needed here as it's set in init()
        log.info("EchoExtension配置阶段: extensionName={}", env.getExtensionName());

        // 获取配置属性示例
        env.getPropertyString("echo.prefix") // 替换为getPropertyString
            .ifPresent(prefix -> log.info("EchoExtension配置前缀: {}", prefix));
    }

    @Override
    public void onInit(TenEnv env) {
        super.onInit(env); // Call super method
        log.info("EchoExtension初始化阶段: extensionName={}", env.getExtensionName());

        // 模拟一些初始化工作，通过 env.postTask 避免阻塞 Runloop 线程
        env.postTask(() -> {
            try {
                Thread.sleep(100); // 模拟初始化耗时
                log.info("EchoExtension {} 模拟初始化工作完成。", env.getExtensionName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("EchoExtension {} 模拟初始化工作被中断。", env.getExtensionName(), e);
            }
        });
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env); // Call super method
        log.info("EchoExtension启动阶段: extensionName={}", env.getExtensionName());
        isRunning = true;
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env); // Call super method
        log.info("EchoExtension停止阶段: extensionName={}", env.getExtensionName());
        isRunning = false;
    }

    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env); // Call super method
        log.info("EchoExtension清理阶段: extensionName={}", env.getExtensionName());
        log.info("EchoExtension统计信息: 处理消息总数={}", messageCount);
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        // super.onCommand(command, env); // Removed direct call to super.onCommand
        if (!isRunning) {
            log.warn("EchoExtension未运行，忽略命令: extensionName={}, commandName={}",
                env.getExtensionName(), command.getName());
            return;
        }

        messageCount++;
        log.info("EchoExtension收到命令: extensionName={}, commandName={}, commandId={}",
            env.getExtensionName(), command.getName(), command.getId()); // 修正为 getId()

        // 使用 TenEnv 提交异步任务（模拟异步操作）
        env.postTask(() -> {
            try {
                // 模拟一些处理时间
                Thread.sleep(50);

                // 创建回显结果
                String detailJson = OBJECT_MAPPER.writeValueAsString(new java.util.HashMap<String, Object>() {
                    {
                        put("original_command", command.getName());
                        put("echo_content", "Echo: " + command.getName());
                        put("processed_by", env.getExtensionName());
                        put("message_count", messageCount);
                    }
                });
                CommandResult result = CommandResult.success(command, detailJson);
                result.setName("echo_result");

                // 发送结果
                env.sendResult(result);
                log.debug("EchoExtension命令处理完成: extensionName={}, commandName={}",
                    env.getExtensionName(), command.getName());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("EchoExtension命令处理被中断: extensionName={}, commandName={}",
                    env.getExtensionName(), command.getName());
            } catch (Exception e) {
                log.error("EchoExtension命令处理异常: extensionName={}, commandName={}",
                    env.getExtensionName(), command.getName(), e);
            }
        });
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage data) { // Changed method name and parameter type
        // super.onDataMessage(data, env); // Removed direct call to super.onDataMessage
        if (!isRunning) {
            log.warn("EchoExtension未运行，忽略数据: extensionName={}, dataName={}",
                env.getExtensionName(), data.getName());
            return;
        }

        messageCount++;
        log.info("EchoExtension收到数据: extensionName={}, dataName={}, properties={}",
            env.getExtensionName(), data.getName(), data.getProperties());

        // 使用 TenEnv 提交异步任务
        env.postTask(() -> {
            try {
                // 模拟数据处理时间
                Thread.sleep(30);

                // 创建回显数据
                DataMessage echoData = DataMessage.create("text_data");
                echoData.setProperty("original_data_name", data.getName());
                echoData.setProperty("text", "Echo%s: Hello ".formatted(data.getProperty("text")));

                // 设置目标位置（如果有的话）
                if (data.getDestLocs() != null && !data.getDestLocs().isEmpty()) { // 修正为 getDestLocs()
                    echoData.setDestLocs(data.getDestLocs()); // 修正为 setDestLocs()
                }

                // 发送回显数据
                env.sendMessage(echoData);
                log.debug("EchoExtension数据处理完成: extensionName={}, dataName={}",
                    env.getExtensionName(), data.getName());

            } catch (Exception e) {
                log.error("EchoExtension数据处理异常: extensionName={}, dataName={}",
                    env.getExtensionName(), data.getName(), e);
            }
        });
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) { // Changed parameter type
        // super.onAudioFrame(audioFrame, env); // Removed direct call to
        // super.onAudioFrame
        if (!isRunning) {
            log.warn("EchoExtension未运行，忽略音频帧: extensionName={}, frameName={}",
                env.getExtensionName(), audioFrame.getName());
            return;
        }

        messageCount++;
        log.debug("EchoExtension收到音频帧: extensionName={}, frameName={}, frameSize={}, sampleRate={}, channels={}",
            env.getExtensionName(), audioFrame.getName(), audioFrame.getDataBytes().length,
            audioFrame.getSampleRate(), audioFrame.getNumberOfChannel()); // 修正为 getNumberOfChannel()

        // 音频帧通常不需要回显，只记录信息
        // 这里可以添加音频处理逻辑，如VAD检测等
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) { // Changed parameter type
        // super.onVideoFrame(videoFrame, env); // Removed direct call to
        // super.onVideoFrame
        if (!isRunning) {
            log.warn("EchoExtension未运行，忽略视频帧: extensionName={}, frameName={}",
                env.getExtensionName(), videoFrame.getName());
            return;
        }

        messageCount++;
        log.debug("EchoExtension收到视频帧: extensionName={}, frameName={}, frameSize={}, width={}, height={}",
            env.getExtensionName(), videoFrame.getName(), videoFrame.getDataBytes().length,
            videoFrame.getWidth(), videoFrame.getHeight());

        // 视频帧通常不需要回显，只记录信息
        // 这里可以添加视频处理逻辑，如帧率控制等
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) { // Changed parameter type
        // super.onCommandResult(commandResult, env); // Removed direct call to
        // super.onCommandResult
        log.warn("EchoExtension收到未处理的 CommandResult: {}. OriginalCommandId: {}", env.getExtensionName(),
            commandResult.getId(), commandResult.getOriginalCommandId());
    }
}