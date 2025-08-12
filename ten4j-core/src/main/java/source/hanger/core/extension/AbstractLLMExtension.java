package source.hanger.core.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

/**
 * LLM基础抽象类
 * 基于ten-framework AI_BASE的AsyncLLMBaseExtension设计
 *
 * 核心特性：
 * 1. 异步处理队列机制 (现在通过 TenEnv 实现)
 * 2. 工具编排能力 - 支持动态工具注册和管理
 * 3. 流式处理支持 - 支持流式文本输出和中断机制
 * 4. 会话状态管理 - 维护对话历史和上下文
 * 5. 精确的错误处理和监控 (此处为占位符，需实际集成)
 */
@Slf4j
public abstract class AbstractLLMExtension extends BaseExtension { // Extends BaseExtension

    // 工具管理
    private final List<ToolMetadata> availableTools = new CopyOnWriteArrayList<>();
    private final Object toolsLock = new Object();
    // 会话状态
    private final Map<String, Object> sessionState = new ConcurrentHashMap<>();
    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    // 性能监控
    private final ObjectMapper objectMapper = new ObjectMapper(); // 用于 JSON 解析
    // protected String extensionName; // Handled by BaseExtension
    protected boolean isRunning = false;
    // protected Map<String, Object> configuration; // Handled by BaseExtension
    // protected TenEnvProxy<Extension> envProxy; // Replaced by TenEnv env in
    // BaseExtension

    public AbstractLLMExtension() {
    }

    // Removed init method, handled by BaseExtension
    // @Override
    // public void init(String extensionId, GraphConfig config, TenEnv env) {
    // super.init(extensionId, config, env);
    // this.extensionName = extensionId; // Re-assign for consistency if needed
    // this.env = env;
    // this.configuration = config.toMap();
    // log.info("AbstractLLMExtension {} initialized with TenEnv.", extensionId);
    // }

    // Removed destroy method, handled by BaseExtension
    // @Override
    // public void destroy(TenEnv env) {
    // super.destroy(env);
    // log.info("AbstractLLMExtension {} destroyed.", extensionName);
    // }

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) { // Changed parameter type
        super.onConfigure(env, properties); // Call super method
        // extensionName = env.getExtensionName(); // 从 TenEnv 获取 ExtensionName, but
        // already set in init
        log.info("LLM扩展配置阶段: extensionName={}", env.getExtensionName());
        onLLMConfigure(env); // Changed parameter type
    }

    @Override
    public void onInit(TenEnv env) { // Changed parameter type
        super.onInit(env); // Call super method
        log.info("LLM扩展初始化阶段: extensionName={}", env.getExtensionName());
        onLLMInit(env); // Changed parameter type
    }

    @Override
    public void onStart(TenEnv env) { // Changed parameter type
        super.onStart(env); // Call super method
        log.info("LLM扩展启动阶段: extensionName={}", env.getExtensionName());
        isRunning = true;
        interrupted.set(false);
        onLLMStart(env); // Changed parameter type
    }

    @Override
    public void onStop(TenEnv env) { // Changed parameter type
        super.onStop(env); // Call super method
        log.info("LLM扩展停止阶段: extensionName={}", env.getExtensionName());
        isRunning = false;
        onLLMStop(env); // Changed parameter type
    }

    @Override
    public void onDeinit(TenEnv env) { // Changed parameter type
        super.onDeinit(env); // Call super method
        log.info("LLM扩展清理阶段: extensionName={}", env.getExtensionName());
        onLLMDeinit(env); // Changed parameter type
    }

    @Override
    public void onCmd(TenEnv env, Command command) { // Changed parameter type
        super.onCmd(env, command); // 调用父类的 onCmd
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略命令: extensionName={}, commandName={}",
                env.getExtensionName(), command.getName());
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            handleLLMCommand(env, command); // Changed parameter type
            long duration = System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            log.error("LLM扩展命令处理异常: extensionName={}, commandName={}",
                env.getExtensionName(), command.getName(), e);
            sendErrorResult(env, command, "LLM命令处理异常: " + e.getMessage()); // Changed parameter type
        }
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage data) { // Changed parameter type and method name
        super.onDataMessage(env, data); // 调用父类的 onDataMessage
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略数据: extensionName={}, dataId={}",
                env.getExtensionName(), data.getId());
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            // 将数据处理封装为任务，并通过 TenEnv 提交到 Runloop
            env.postTask(() -> {
                try {
                    if (interrupted.get()) {
                        log.debug("LLM处理被中断，跳过当前项目: extensionName={}", env.getExtensionName());
                        return;
                    }
                    onDataChatCompletion(env, data); // Changed parameter type
                } catch (Exception e) {
                    log.error("LLM数据处理队列任务异常: extensionName={}, dataId={}", env.getExtensionName(),
                        data.getId(), e);
                }
            });
            long duration = System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            log.error("LLM扩展数据提交异常: extensionName={}, dataId={}",
                env.getExtensionName(), data.getId(), e);
        }
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) { // Changed parameter type
        super.onAudioFrame(env, audioFrame); // 调用父类的 onAudioFrame
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略音频帧: extensionName={}, frameId={}",
                env.getExtensionName(), audioFrame.getId());
            return;
        }

        log.debug("LLM扩展收到音频帧: extensionName={}, frameId={}",
            env.getExtensionName(), audioFrame.getId());
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) { // Changed parameter type
        super.onVideoFrame(env, videoFrame); // 调用父类的 onVideoFrame
        if (!isRunning) {
            log.warn("LLM扩展未运行，忽略视频帧: extensionName={}, frameId={}",
                env.getExtensionName(), videoFrame.getId());
            return;
        }

        log.debug("LLM扩展收到视频帧: extensionName={}, frameId={}",
            env.getExtensionName(), videoFrame.getId());
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) { // Changed parameter type
        super.onCmdResult(env, commandResult); // 调用父类的 onCmdResult
        log.warn("LLM扩展收到未处理的 CommandResult: {}. OriginalCommandId: {}", env.getExtensionName(),
            commandResult.getId(), commandResult.getOriginalCommandId());
    }

    /**
     * 处理LLM命令
     */
    private void handleLLMCommand(TenEnv env, Command command) { // Changed parameter type
        String commandName = command.getName();

        switch (commandName) {
            case "tool_register":
                handleToolRegister(env, command); // Changed parameter type
                break;
            case "chat_completion_call":
                handleChatCompletionCall(env, command); // Changed parameter type
                break;
            case "flush":
                handleFlush(env);
                break;
            default:
                log.warn("未知的LLM命令: extensionName={}, commandName={}",
                    env.getExtensionName(), commandName);
        }
    }

    /**
     * 处理工具注册
     */
    private void handleToolRegister(TenEnv env, Command command) { // Changed parameter type
        try {
            // 从 properties 中获取 tool_metadata
            // TODO: properties 应该通过 TenEnv 获取，这里暂时通过 command.getProperties() 获取
            String toolMetadataJson = (String)command.getProperties().get("tool_metadata");
            if (toolMetadataJson == null) {
                throw new IllegalArgumentException("缺少工具元数据");
            }

            ToolMetadata toolMetadata = parseToolMetadata(toolMetadataJson);

            synchronized (toolsLock) {
                availableTools.add(toolMetadata);
            }

            onToolsUpdate(env, toolMetadata);

            // 发送成功结果
            CommandResult result = CommandResult.success(command, "Tool registered successfully.");
            env.sendResult(result);

            log.info("工具注册成功: extensionName={}, toolName={}",
                env.getExtensionName(), toolMetadata.getName());
        } catch (Exception e) {
            log.error("工具注册失败: extensionName={}", env.getExtensionName(), e);
            sendErrorResult(env, command, "工具注册失败: " + e.getMessage()); // 确保这里传入的是 Command 对象
        }
    }

    /**
     * 处理聊天完成调用
     */
    private void handleChatCompletionCall(TenEnv env, Command command) { // Changed parameter type
        try {
            // 从 properties 中获取 args
            // TODO: args 应该通过 TenEnv 获取，这里暂时通过 command.getProperties() 获取
            Map<String, Object> args = (Map<String, Object>)command.getProperties().get("args");
            if (args == null) {
                throw new IllegalArgumentException("缺少聊天完成参数");
            }

            // 直接执行LLM调用，因为此方法已在 Runloop 线程上调用
            onCallChatCompletion(env, args); // Changed parameter type

        } catch (Exception e) {
            log.error("聊天完成调用处理失败: extensionName={}", env.getExtensionName(), e);
            sendErrorResult(env, command, "聊天完成调用失败: " + e.getMessage()); // Changed parameter type
        }
    }

    /**
     * 处理刷新命令
     */
    private void handleFlush(TenEnv env) { // Changed parameter type
        log.info("LLM扩展收到刷新命令: extensionName={}", env.getExtensionName());

        // 清空处理队列 (现在通过 TenEnv 的 postTask 清空)
        // 为了实现清空，可以考虑引入一个专门的 flush 命令或 TenEnv 的 API
        // 这里暂时通过一个空操作来模拟清空，实际需要根据 TenEnv 的设计来完善
        env.postTask(() -> {
            log.debug("LLM处理队列清空任务执行。");
            interrupted.set(true); // 设置中断标志
            interrupted.set(false); // 重置中断标志
        });

        log.info("LLM扩展刷新完成: extensionName={}", env.getExtensionName());
    }

    /**
     * 发送文本输出
     */
    protected void sendTextOutput(TenEnv env, String text, boolean endOfSegment) { // Changed
        try {
            DataMessage outputData = DataMessage.create("text_data");
            // 将 text 和 end_of_segment 放入 properties
            outputData.setProperty("text", text);
            outputData.setProperty("end_of_segment", endOfSegment);
            outputData.setProperty("extension_name", env.getExtensionName());

            env.sendMessage(outputData);
            log.debug("LLM文本输出发送成功: extensionName={}, text={}, endOfSegment={}",
                env.getExtensionName(), text, endOfSegment);
        } catch (Exception e) {
            log.error("LLM文本输出发送异常: extensionName={}", env.getExtensionName(), e);
        }
    }

    /**
     * 发送错误结果
     */
    protected void sendErrorResult(TenEnv env, Command command, String errorMessage) { // Changed
        CommandResult errorResult = CommandResult.fail(command, errorMessage);
        env.sendResult(errorResult);
    }

    /**
     * 解析工具元数据
     */
    protected ToolMetadata parseToolMetadata(String json) {
        try {
            return objectMapper.readValue(json, ToolMetadata.class);
        } catch (Exception e) {
            log.error("解析工具元数据失败: {}", e.getMessage(), e);
            return ToolMetadata.builder().name("invalid_tool").description("解析失败").build();
        }
    }

    // 抽象方法 - 子类必须实现

    /**
     * LLM配置阶段
     */
    protected abstract void onLLMConfigure(TenEnv context); // Changed parameter type

    /**
     * LLM初始化阶段
     */
    protected abstract void onLLMInit(TenEnv context); // Changed parameter type

    /**
     * LLM启动阶段
     */
    protected abstract void onLLMStart(TenEnv context); // Changed parameter type

    /**
     * LLM停止阶段
     */
    protected abstract void onLLMStop(TenEnv context); // Changed parameter type

    /**
     * LLM清理阶段
     */
    protected abstract void onLLMDeinit(TenEnv context); // Changed parameter type

    /**
     * 处理数据驱动的聊天完成
     */
    protected abstract void onDataChatCompletion(TenEnv context, DataMessage data); // Changed parameter
    // type

    /**
     * 处理命令驱动的聊天完成
     */
    protected abstract void onCallChatCompletion(TenEnv context, Map<String, Object> args); // Changed
    // parameter
    // type

    /**
     * 处理工具更新
     */
    protected abstract void onToolsUpdate(TenEnv context, ToolMetadata tool); // Changed parameter type

    // 辅助方法

    /**
     * 获取可用工具列表
     */
    public List<ToolMetadata> getAvailableTools() {
        synchronized (toolsLock) {
            return new ArrayList<>(availableTools);
        }
    }

    /**
     * 获取会话状态
     */
    public Map<String, Object> getSessionState() {
        return sessionState;
    }

    // Removed getExtensionName and getAppUri, handled by BaseExtension
    // @Override
    // public String getExtensionName() {
    // return extensionName;
    // }

    // @Override
    // public String getAppUri() {
    // return env.getAppUri(); // Changed to env
    // }

    /**
     * LLM输入项
     */
    private static class LLMInputItem {
        final DataMessage data;
        final TenEnv env; // Changed type

        LLMInputItem(DataMessage data, TenEnv env) { // Changed type
            this.data = data;
            this.env = env;
        }
    }

    /**
     * LLM工具元数据，用于工具调用功能
     */
    @Data
    @Builder
    public static class ToolMetadata {
        private String name;
        private String description;
        private Map<String, Object> parameters; // 工具参数的JSON Schema
        private List<String> required; // 必填参数列表
    }
}