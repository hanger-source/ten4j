package com.tenframework.core.extension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenframework.core.message.AudioFrameMessage;
import com.tenframework.core.message.CommandResult;
import com.tenframework.core.message.DataMessage;
import com.tenframework.core.message.VideoFrameMessage;
import com.tenframework.core.message.command.Command;
import com.tenframework.core.tenenv.TenEnv;
import lombok.extern.slf4j.Slf4j;

/**
 * 简单的工具扩展示例，用于演示如何处理不同类型的消息。
 */
@Slf4j
public class SimpleToolExtension extends AbstractToolProvider {

    private static final ObjectMapper objectMapper = new ObjectMapper(); // 在 SimpleToolExtension 中创建自己的 ObjectMapper 实例
    private long messageCount = 0;

    @Override
    protected List<ToolMetadata> initializeTools() { // 修复：返回类型为 List<ToolMetadata>
        // 默认空实现，子类可以在这里初始化工具
        log.info("SimpleToolExtension: initializeTools called.");
        return Collections.emptyList(); // 修复：返回一个空的 List<ToolMetadata>
    }

    @Override
    protected void onToolProviderConfigure(TenEnv context) { // 修复：实现抽象方法
        // 默认空实现
        log.info("SimpleToolExtension: onToolProviderConfigure called.");
    }

    @Override
    protected void onToolProviderInit(TenEnv context) { // 修复：实现抽象方法
        // 默认空实现
        log.info("SimpleToolExtension: onToolProviderInit called.");
    }

    @Override
    protected void onToolProviderStart(TenEnv context) { // 修复：实现抽象方法
        // 默认空实现
        log.info("SimpleToolExtension: onToolProviderStart called.");
    }

    @Override
    protected void onToolProviderStop(TenEnv context) { // 修复：实现抽象方法
        // 默认空实现
        log.info("SimpleToolExtension: onToolProviderStop called.");
    }

    @Override
    protected void onToolProviderDeinit(TenEnv context) { // 修复：实现抽象方法
        // 默认空实现
        log.info("SimpleToolExtension: onToolProviderDeinit called.");
    }

    @Override
    public void onInit(TenEnv env) {
        log.info("SimpleToolExtension: {} onInit called.", getExtensionName());
        // 可以在这里加载工具配置或进行其他初始化
    }

    @Override
    public void onStart(TenEnv env) {
        log.info("SimpleToolExtension: {} onStart called.", getExtensionName());
        // 可以在这里启动工具服务
    }

    @Override
    public void onStop(TenEnv env) {
        log.info("SimpleToolExtension: {} onStop called.", getExtensionName());
        // 可以在这里停止工具服务
    }

    @Override
    public void onDeinit(TenEnv env) {
        log.info("SimpleToolExtension: {} onDeinit called.", getExtensionName());
        // 可以在这里释放工具资源
    }

    @Override
    protected void handleToolCommand(TenEnv context, Command command) {
        log.debug("工具扩展收到命令: {}", command.getName());
        // 模拟处理命令并发送结果
        String detailJson;
        try {
            detailJson = objectMapper.writeValueAsString(Map.of("tool_response", "Hello from Tool!"));
        } catch (IOException e) {
            log.error("Failed to serialize tool command result detail: {}", e.getMessage(), e);
            detailJson = "Error: Failed to serialize result.";
        }
        sendSuccessResult(context, command, detailJson); // 修复：调用父类的 sendSuccessResult 方法，传递序列化后的字符串
    }

    @Override
    protected void handleToolData(TenEnv context, DataMessage data) {
        log.debug("工具扩展收到数据: {}", new String(data.getData()));
        // 模拟处理数据并可能触发其他命令或发送数据
    }

    @Override
    protected void handleToolAudioFrame(TenEnv context, AudioFrameMessage audioFrame) {
        log.debug("工具扩展收到音频帧: {} ({}Hz, {}ch)", audioFrame.getName(),
                audioFrame.getSampleRate(), audioFrame.getNumberOfChannel()); // 修复：getChannels() 改为
                                                                              // getNumberOfChannel()
        // 模拟处理音频帧
    }

    @Override
    protected void handleToolVideoFrame(TenEnv context, VideoFrameMessage videoFrame) {
        log.debug("工具扩展收到视频帧: {}", videoFrame.getName());
        // 模拟处理视频帧
    }

    @Override
    public void onCmdResult(TenEnv env, CommandResult commandResult) { // 修复：方法名和访问修饰符
        log.debug("工具扩展收到命令结果: {}", commandResult.getId());
        // 处理上游命令的结果
    }

    @Override
    public String getExtensionName() {
        return "SimpleToolExtension";
    }
}