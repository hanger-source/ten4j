package source.hanger.core.extension;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Location;
import source.hanger.core.message.MessageConstants;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;
import lombok.extern.slf4j.Slf4j;

/**
 * 简单的Echo Extension示例
 * 展示开发者如何开箱即用，只需实现核心业务逻辑
 *
 * 开发者只需要：
 * 1. 继承BaseExtension
 * 2. 实现4个简单的handle方法
 * 3. 可选实现生命周期方法
 *
 * 所有底层能力都由BaseExtension提供：
 * - 自动生命周期管理
 * - 内置异步处理
 * - 自动错误处理和重试
 * - 内置性能监控
 * - 自动资源管理
 */
@Slf4j
public class SimpleEchoExtension extends BaseExtension {

    private static final ObjectMapper objectMapper = new ObjectMapper(); // Add this static final field
    private String echoPrefix = "Echo: ";
    private long messageCount = 0;

    @Override
    public void onConfigure(TenEnv env, Map<String, Object> properties) { // 修复：方法名和访问修饰符
        super.onConfigure(env, properties); // 修复：调用父类方法
        // 从配置中读取echo前缀
        env.getPropertyString("echo.prefix") // 替换为getPropertyString
                .ifPresent(prefix -> {
                    this.echoPrefix = prefix; // 设置前缀
                    log.info("Echo前缀配置: {}", prefix);
                });
    }

    @Override
    public void onCmd(TenEnv env, Command command) { // 修复：方法名和访问修饰符
        // 开发者只需关注业务逻辑
        String commandName = command.getName();
        log.info("收到命令: {}", commandName);

        // 简单的回显逻辑
        String echoMessage = echoPrefix + commandName;

        // 使用BaseExtension提供的便捷方法发送结果
        String detailJson;
        try {
            detailJson = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {
                {
                    put("echo_message", echoMessage);
                    put("count", ++messageCount);
                }
            });
        } catch (IOException e) {
            log.error("Failed to serialize command result detail: {}", e.getMessage(), e);
            detailJson = "Error: Failed to serialize result.";
        }
        CommandResult result = CommandResult.success(command.getId(), detailJson);
        // result.setSourceLocation(new Location(context.getAppUri(),
        // context.getGraphId(), context.getExtensionName())); // srcLoc 应该在构造时传入
        env.sendResult(result);
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage data) { // 修复：方法名和访问修饰符
        String dataName = data.getName();
        log.info("SimpleEchoExtension收到数据: name={}, sourceLocation={}",
                dataName, data.getSrcLoc());
        log.debug("SimpleEchoExtension: Message properties at onData start: {}", data.getProperties()); // Debug log
        if (data.getDataBytes() != null && data.getDataBytes().length > 0) {
            log.debug("原始数据大小: {} bytes", data.getDataSize());
        } else {
            log.debug("原始数据不包含有效负载。");
        }

        try {
            // 1. 解析原始数据内容为Map
            byte[] rawDataBytes = data.getDataBytes();
            if (rawDataBytes.length == 0) {
                log.warn("SimpleEchoExtension收到空数据或null数据，无法处理回显。");
                return;
            }
            log.debug("尝试将原始数据解析为JSON Map, 长度: {} bytes", rawDataBytes.length);
            Map<String, Object> originalPayload = objectMapper.readValue(rawDataBytes, Map.class);
            log.debug("原始数据解析成功: {}", originalPayload);
            String originalContent = (String) originalPayload.get("content");
            if (originalContent == null) {
                log.warn("原始数据payload中未找到'content'字段，跳过回显处理。");
                return;
            }

            String echoContent = echoPrefix + originalContent; // 只对原始内容进行前缀
            log.debug("回显内容: {}", echoContent);

            // 2. 更新payload中的content
            originalPayload.put("content", echoContent);
            log.debug("更新后的payload: {}", originalPayload);

            // 使用ObjectMapper将Map序列化回字节数组
            byte[] echoDataBytes = objectMapper.writeValueAsBytes(originalPayload);

            // source Location 作为destination Location
            Location destinationLocation = new Location(env.getAppUri(), env.getGraphId(),
                    data.getSrcLoc().getExtensionName()); // 修复：extensionName() 改为 getExtensionName()

            // 构造新的 DataMessage
            DataMessage echoData = new DataMessage(source.hanger.core.util.MessageUtils.generateUniqueId(),
                    MessageType.DATA,
                    new Location().setAppUri(env.getAppUri()).setGraphId(env.getGraphId())
                            .setExtensionName(env.getExtensionName()), // 修复：getCurrentLocation() 替换为构建Location
                    Collections.singletonList(destinationLocation), echoDataBytes); // 使用新的构造函数

            echoData.setProperties(new java.util.HashMap<String, Object>() {
                {
                    put("original_name", dataName);
                    put("count", ++messageCount);
                    put("msgpack_ext_type", Byte.valueOf(MessageConstants.TEN_MSGPACK_EXT_TYPE_MSG));
                }
            });

            // 通过 EngineTenEnv 提交回显数据
            env.sendData(echoData); // 将sendMessage替换为sendData
            log.info("SimpleEchoExtension发送回显数据: name={}", echoData.getName());
        } catch (IOException e) {
            log.error("处理数据解析/序列化时发生错误: {}", e.getMessage(), e);
            // 可以在这里发送一个错误消息回客户端，或者只是记录日志并丢弃消息
        } catch (Exception e) {
            log.error("SimpleEchoExtension处理数据时发生意外错误: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) { // 修复：方法名和访问修饰符
        // 开发者只需关注业务逻辑
        log.debug("收到音频帧: {} ({} bytes)", audioFrame.getName(), audioFrame.getDataSize());

        // 简单的音频处理逻辑
        // 这里可以添加音频分析、VAD检测等
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) { // 修复：方法名和访问修饰符
        // 开发者只需关注业务逻辑
        log.debug("收到视频帧: {} ({}x{})", videoFrame.getName(),
                videoFrame.getWidth(), videoFrame.getHeight());

        // 简单的视频处理逻辑
        // 这里可以添加视频分析、帧率控制等
    }

    // 可选：自定义配置
    protected void onExtensionConfigure(TenEnv env) {
        // 从配置中读取echo前缀
        log.info("Echo前缀配置: {}", echoPrefix);
    }

    // 可选：自定义健康检查
    protected boolean performHealthCheck() {
        // 简单的健康检查：消息计数正常
        return messageCount > 0 && getErrorCount() < 10;
    }
}