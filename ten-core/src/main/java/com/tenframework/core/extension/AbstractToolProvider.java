package com.tenframework.core.extension;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.graph.GraphConfig;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.tenenv.TenEnv;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具提供者基础抽象类
 * 用于提供各种工具服务，如文件操作、网络请求等
 *
 * 功能：
 * 1. 管理工具元数据
 * 2. 提供工具执行接口
 * 3. 支持工具参数验证
 * 4. 处理工具执行结果
 */
@Slf4j
public abstract class AbstractToolProvider extends BaseExtension { // Extend BaseExtension

    private final ObjectMapper objectMapper = new ObjectMapper(); // 用于 JSON 解析
    // protected String extensionName; // Handled by BaseExtension
    protected boolean isRunning = false;
    // protected Map<String, Object> configuration; // Handled by BaseExtension
    protected List<ToolMetadata> availableTools;
    // protected TenEnvProxy<Extension> envProxy; // Replaced by TenEnv env in
    // BaseExtension

    // Removed init method, handled by BaseExtension
    // @Override
    // public void init(String extensionId, GraphConfig config, TenEnv env) {
    // super.init(extensionId, config, env);
    // this.extensionName = extensionId;
    // this.env = env;
    // this.configuration = config.toMap();
    // log.info("AbstractToolProvider {} initialized with TenEnv.", extensionId);
    // }

    // Removed destroy method, handled by BaseExtension
    // @Override
    // public void destroy(TenEnv env) {
    // super.destroy(env);
    // log.info("AbstractToolProvider {} destroyed.", extensionName);
    // }

    @Override
    public void onConfigure(TenEnv env) { // Changed parameter type
        super.onConfigure(env); // Call super method
        // extensionName = env.getExtensionName(); // 从 TenEnv 获取
        log.info("工具提供者配置阶段: extensionName={}", extensionName != null ? extensionName : "(未设置)");
        onToolProviderConfigure(env); // Changed parameter type
    }

    @Override
    public void onInit(TenEnv env) { // Changed parameter type
        super.onInit(env); // Call super method
        log.info("工具提供者初始化阶段: extensionName={}", extensionName != null ? extensionName : "(未设置)");
        availableTools = initializeTools();
        onToolProviderInit(env); // Changed parameter type
    }

    @Override
    public void onStart(TenEnv env) { // Changed parameter type
        super.onStart(env); // Call super method
        log.info("工具提供者启动阶段: extensionName={}", extensionName != null ? extensionName : "(未设置)");
        isRunning = true;
        onToolProviderStart(env); // Changed parameter type
    }

    @Override
    public void onStop(TenEnv env) { // Changed parameter type
        super.onStop(env); // Call super method
        log.info("工具提供者停止阶段: extensionName={}", extensionName != null ? extensionName : "(未设置)");
        isRunning = false;
        onToolProviderStop(env); // Changed parameter type
    }

    @Override
    public void onDeinit(TenEnv env) { // Changed parameter type
        super.onDeinit(env); // Call super method
        log.info("工具提供者清理阶段: extensionName={}", extensionName != null ? extensionName : "(未设置)");
        onToolProviderDeinit(env); // Changed parameter type
    }

    @Override
    public void onCmd(TenEnv env, Command command) { // Changed parameter type
        super.onCmd(env, command); // 调用父类的 onCmd
        if (!isRunning) {
            log.warn("工具提供者未运行，忽略命令: extensionName={}, commandName={}",
                    extensionName, command.getName());
            return;
        }

        log.debug("工具提供者收到命令: extensionName={}, commandName={}",
                extensionName, command.getName());

        // 直接处理工具命令，因为此方法已在 Runloop 线程上调用
        try {
            handleToolCommand(env, command);
        } catch (Exception e) {
            log.error("工具提供者命令处理异常: extensionName={}, commandName={}",
                    extensionName, command.getName(), e);
            sendErrorResult(env, command, "工具执行异常: " + e.getMessage());
        }
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage data) { // Changed parameter type and method name
        super.onDataMessage(env, data); // 调用父类的 onDataMessage
        if (!isRunning) {
            log.warn("工具提供者未运行，忽略数据: extensionName={}, dataId={}",
                    extensionName, data.getId());
            return;
        }

        log.debug("工具提供者收到数据: extensionName={}, dataId={}",
                extensionName, data.getId());
        // 直接处理数据
        try {
            handleToolData(env, data);
        } catch (Exception e) {
            log.error("工具提供者数据处理异常: extensionName={}, dataId={}",
                    extensionName, data.getId(), e);
        }
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) { // Changed parameter type
        super.onAudioFrame(env, audioFrame); // 调用父类的 onAudioFrame
        if (!isRunning) {
            log.warn("工具提供者未运行，忽略音频帧: extensionName={}, frameId={}",
                    extensionName, audioFrame.getId());
            return;
        }

        log.debug("工具提供者收到音频帧: extensionName={}, frameId={}",
                extensionName, audioFrame.getId());
        // 直接处理音频帧
        try {
            handleToolAudioFrame(env, audioFrame);
        } catch (Exception e) {
            log.error("工具提供者音频帧处理异常: extensionName={}, frameId={}",
                    extensionName, audioFrame.getId(), e);
        }
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) { // Changed parameter type
        super.onVideoFrame(env, videoFrame); // 调用父类的 onVideoFrame
        if (!isRunning) {
            log.warn("工具提供者未运行，忽略视频帧: extensionName={}, frameId={}",
                    extensionName, videoFrame.getId());
            return;
        }

        log.debug("工具提供者收到视频帧: extensionName={}, frameId={}",
                extensionName, videoFrame.getId());
        // 直接处理视频帧
        try {
            handleToolVideoFrame(env, videoFrame);
        } catch (Exception e) {
            log.error("工具提供者视频帧处理异常: extensionName={}, videoFrameId={}",
                    extensionName, videoFrame.getId(), e);
        }
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) { // Changed parameter type
        super.onCmdResult(env, commandResult); // 调用父类的 onCmdResult
        log.warn("Extension {} received unhandled CommandResult: {}. OriginalCommandId: {}", getExtensionName(),
                commandResult.getId(), commandResult.getOriginalCommandId());
    }

    /**
     * 工具提供者配置阶段
     */
    protected abstract void onToolProviderConfigure(TenEnv context); // Changed parameter type

    /**
     * 工具提供者初始化阶段
     */
    protected abstract void onToolProviderInit(TenEnv context); // Changed parameter type

    /**
     * 工具提供者启动阶段
     */
    protected abstract void onToolProviderStart(TenEnv context); // Changed parameter type

    /**
     * 工具提供者停止阶段
     */
    protected abstract void onToolProviderStop(TenEnv context); // Changed parameter type

    /**
     * 工具提供者清理阶段
     */
    protected abstract void onToolProviderDeinit(TenEnv context); // Changed parameter type

    /**
     * 初始化可用工具列表
     */
    protected abstract List<ToolMetadata> initializeTools();

    /**
     * 处理工具命令
     */
    protected abstract void handleToolCommand(TenEnv context, Command command);

    /**
     * 处理工具数据
     */
    protected abstract void handleToolData(TenEnv context, DataMessage data);

    /**
     * 处理工具音频帧
     */
    protected abstract void handleToolAudioFrame(TenEnv context, AudioFrameMessage audioFrame);

    /**
     * 处理工具视频帧
     */
    protected abstract void handleToolVideoFrame(TenEnv context, VideoFrameMessage videoFrame);

    /**
     * 发送错误结果
     */
    protected void sendErrorResult(TenEnv context, Command command, String errorMessage) {
        CommandResult errorResult = CommandResult.fail(command.getId(), errorMessage);
        context.sendResult(errorResult);
    }

    /**
     * 发送成功结果
     */
    protected void sendSuccessResult(TenEnv context, Command command, Object result) {
        CommandResult successResult = CommandResult.success(command.getId(), result != null ? result.toString() : "");
        context.sendResult(successResult);
    }

    // Removed getAppUri and getExtensionName, handled by BaseExtension
    // @Override
    // public String getAppUri() {
    // return env.getAppUri();
    // }

    // @Override
    // public String getExtensionName() {
    // return extensionName;
    // }

    /**
     * 工具元数据，用于工具调用功能
     * 与 AbstractLLMExtension 中的定义保持一致
     */
    @Data
    @Builder
    public static class ToolMetadata {
        @JsonProperty("name")
        private String name;
        @JsonProperty("description")
        private String description;
        @JsonProperty("parameters")
        private Map<String, Object> parameters; // 工具参数的JSON Schema
        @JsonProperty("required")
        private List<String> required; // 必填参数列表

        // 默认构造函数用于 Jackson
        public ToolMetadata() {
        }

        // AllArgsConstructor 用于 Builder
        public ToolMetadata(String name, String description, Map<String, Object> parameters, List<String> required) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
            this.required = required;
        }
    }
}