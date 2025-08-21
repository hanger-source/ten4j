package source.hanger.core.extension;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

/**
 * 控制器/注入器基础抽象类
 * 用于控制和管理其他Extension，提供注入和编排功能
 *
 * 功能：
 * 1. 管理其他Extension实例
 * 2. 提供Extension编排和路由
 * 3. 处理Extension间的依赖关系
 * 4. 提供Extension生命周期管理
 */
@Slf4j
public abstract class AbstractController extends BaseExtension {

    // 移除 @Getter 和 @Setter，因为不再直接持有 CommandSubmitter 引用
    // protected CommandSubmitter commandSubmitter;

    /**
     * 异步Extension上下文
     */
    // @Getter // 移除Getter，因为已经在BaseExtension中声明
    // protected TenEnv asyncExtensionEnv; // 移除此字段，使用 BaseExtension 的 env 字段

    protected boolean isRunning = false;
    // protected TenEnvProxy<Extension> envProxy; // 移除此字段，使用 BaseExtension 的 env 字段
    // 移除 engine 和 extensionContext 字段

    // @Override // 移除旧的 init 方法
    // public void init(String extensionName, Map<String, Object> properties, TenEnv
    // env) { // 修改签名
    // super.init(extensionName, properties, env); // 调用父类方法
    // this.extensionName = extensionName; // 重新赋值，确保一致
    // // this.env = env; // 已经在父类中设置
    // // this.configuration = config.toMap(); // 移除此行，因为已经在 BaseExtension 的 init
    // 中处理
    // log.info("AbstractController {} initialized with TenEnv.", extensionName);
    // }

    @Override
    public void onDestroy(TenEnv env) {
        super.onDestroy(env); // 调用父类方法
        log.info("AbstractController {} destroyed.", env.getExtensionName()); // 使用 env.getExtensionName()
    }

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        // 配置属性将在子类中通过getProperty方法获取
        log.info("控制器配置阶段: extensionName={}", env.getExtensionName()); // 使用 env.getExtensionName()
        onControllerConfigure(env);
    }

    @Override
    public void onInit(TenEnv env) {
        super.onInit(env); // 调用父类方法
        log.info("控制器初始化阶段: extensionName={}", env.getExtensionName());
        onControllerInit(env);
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env); // 调用父类方法
        log.info("控制器启动阶段: extensionName={}", env.getExtensionName());
        isRunning = true;
        onControllerStart(env);
    }

    @Override
    public void onStop(TenEnv env) {
        super.onStop(env); // 调用父类方法
        log.info("控制器停止阶段: extensionName={}", env.getExtensionName());
        isRunning = false;
        onControllerStop(env);
    }

    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env); // 调用父类方法
        log.info("控制器清理阶段: extensionName={}", env.getExtensionName());
        onControllerDeinit(env);
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        // super.onCommand(command, env); // 不再调用父类的 onCommand，由子类自行处理或选择性调用
        if (!isRunning) {
            log.warn("控制器未运行，忽略命令: extensionName={}, commandName={}",
                    env.getExtensionName(), command.getName());
            return;
        }

        log.debug("控制器收到命令: extensionName={}, commandName={}",
                env.getExtensionName(), command.getName());

        // 直接处理命令，因为此方法已在 Runloop 线程上调用
        try {
            handleControllerCommand(env, command);
        } catch (Exception e) {
            log.error("控制器命令处理异常: extensionName={}, commandName={}",
                    env.getExtensionName(), command.getName(), e);
            sendErrorResult(env, command, "控制器处理异常: " + e.getMessage());
        }
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage data) { // 修正方法名为 onDataMessage
        // super.onDataMessage(data, env); // 不再调用父类的 onDataMessage，由子类自行处理或选择性调用
        if (!isRunning) {
            log.warn("控制器未运行，忽略数据: extensionName={}, dataId={}",
                    env.getExtensionName(), data.getId());
            return;
        }

        log.debug("控制器收到数据: extensionName={}, dataId={}",
                env.getExtensionName(), data.getId());
        handleControllerData(env, data);
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        // super.onAudioFrame(audioFrame, env); // 不再调用父类的 onAudioFrame，由子类自行处理或选择性调用
        if (!isRunning) {
            log.warn("控制器未运行，忽略音频帧: extensionName={}, frameId={}",
                    env.getExtensionName(), audioFrame.getId());
            return;
        }

        log.debug("控制器收到音频帧: extensionName={}, frameId={}",
                env.getExtensionName(), audioFrame.getId());
        handleControllerAudioFrame(env, audioFrame);
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) { // 修正为 VideoMessage
        // super.onVideoFrame(videoFrame, env); // 不再调用父类的 onVideoFrame，由子类自行处理或选择性调用
        if (!isRunning) {
            log.warn("控制器未运行，忽略视频帧: extensionName={}, frameId={}",
                    env.getExtensionName(), videoFrame.getId());
            return;
        }

        log.debug("控制器收到视频帧: extensionName={}, frameId={}",
                env.getExtensionName(), videoFrame.getId());
        handleControllerVideoFrame(env, videoFrame);
    }

    /**
     * 控制器配置阶段
     */
    protected abstract void onControllerConfigure(TenEnv context);

    /**
     * 控制器初始化阶段
     */
    protected abstract void onControllerInit(TenEnv context);

    /**
     * 控制器启动阶段
     */
    protected abstract void onControllerStart(TenEnv context);

    /**
     * 控制器停止阶段
     */
    protected abstract void onControllerStop(TenEnv context);

    /**
     * 控制器清理阶段
     */
    protected abstract void onControllerDeinit(TenEnv context);

    /**
     * 处理控制器命令
     */
    protected abstract void handleControllerCommand(TenEnv context, Command command);

    /**
     * 处理控制器数据
     */
    protected abstract void handleControllerData(TenEnv context, DataMessage data);

    /**
     * 处理控制器音频帧
     */
    protected abstract void handleControllerAudioFrame(TenEnv context, AudioFrameMessage audioFrame);

    /**
     * 处理控制器视频帧
     */
    protected abstract void handleControllerVideoFrame(TenEnv context, VideoFrameMessage videoFrame);

    /**
     * 发送错误结果
     */
    protected void sendErrorResult(TenEnv context, Command command, String errorMessage) {
        CommandResult errorResult = CommandResult.fail(command, errorMessage);
        context.sendResult(errorResult);
    }

    /**
     * 发送成功结果
     */
    protected void sendSuccessResult(TenEnv context, Command command, Object result) {
        CommandResult successResult = CommandResult.success(command,
                result != null ? result.toString() : "");
        context.sendResult(successResult);
    }
}