package source.hanger.core.extension;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Location;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;
import lombok.extern.slf4j.Slf4j;

/**
 * 简单的LLM扩展示例，用于演示如何处理不同类型的消息。
 */
@Slf4j
public class SimpleLLMExtension extends AbstractAIServiceHub {

    @Override
    public void onInit(TenEnv env) {
        log.info("SimpleLLMExtension: {} onInit called.", getExtensionName());
        // 可以在这里加载LLM模型或进行其他初始化
    }

    @Override
    public void onStart(TenEnv env) {
        log.info("SimpleLLMExtension: {} onStart called.", getExtensionName());
        // 可以在这里启动LLM服务
    }

    @Override
    public void onStop(TenEnv env) {
        log.info("SimpleLLMExtension: {} onStop called.", getExtensionName());
        // 可以在这里停止LLM服务
    }

    @Override
    public void onDeinit(TenEnv env) {
        log.info("SimpleLLMExtension: {} onDeinit called.", getExtensionName());
        // 可以在这里释放LLM模型资源
    }

    @Override
    protected void handleAIServiceCommand(TenEnv env, Command command) {
        log.debug("LLM收到命令: {}", command.getName());
        // 模拟处理命令并发送结果
        // env.sendResult(new CommandResult(command.getCommandId(), "LLM Processed: " +
        // command.getName()));
        sendCommandResult(command.getId(), Map.of("llm_response", "Hello from LLM!"), null);
    }

    @Override
    protected void handleAIServiceData(TenEnv env, DataMessage data) {
        log.debug("LLM收到数据: {}", new String(data.getData()));
        // 模拟处理数据并可能触发其他命令或发送数据
        // env.sendMessage(new Data("llm_processed_data", ("Processed: " + new
        // String(data.getData())).getBytes()));
    }

    @Override
    protected void handleAIServiceAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        log.debug("LLM收到音频帧: {} ({}Hz, {}ch)", audioFrame.getName(),
                audioFrame.getSampleRate(), audioFrame.getNumberOfChannel()); // 修复：getChannels() 改为
                                                                              // getNumberOfChannel()
        // 模拟处理音频帧
    }

    @Override
    protected void handleAIServiceVideoFrame(TenEnv env, VideoFrameMessage videoFrame) {
        log.debug("LLM收到视频帧: {} ({}x{})", videoFrame.getName(),
                videoFrame.getWidth(), videoFrame.getHeight());
        // 模拟处理视频帧
    }

    @Override
    protected void handleAIServiceCommandResult(TenEnv env, CommandResult commandResult) {
        log.debug("LLM收到命令结果: {}", commandResult.getId());
        // 处理上游命令的结果
    }

    @Override
    public String getExtensionName() {
        return "SimpleLLMExtension";
    }

    // 示例：LLM可以发送一个命令到其他Extension
    public CompletableFuture<CommandResult> sendLLMCommand(String targetExtension, String commandName,
            Map<String, Object> args) {
        // 使用通用的 Command 构造函数，这里假设我们发送一个 DATA 类型的命令作为示例
        // 实际应用中，这里应该根据具体业务定义 Command 子类
        Command cmd = new Command(
                source.hanger.core.util.MessageUtils.generateUniqueId(), // 修复：添加 id
                new Location().setAppUri(asyncExtensionEnv.getAppUri()).setGraphId(asyncExtensionEnv.getGraphId())
                        .setExtensionName(asyncExtensionEnv.getExtensionName()), // 修复：srcLoc
                MessageType.DATA, // 修复：使用 DATA
                Collections.singletonList(new Location().setAppUri(asyncExtensionEnv.getAppUri())
                        .setGraphId(asyncExtensionEnv.getGraphId()).setExtensionName(targetExtension)),
                // 修复：createLocation()
                // 替换为构建Location
                args, // 修复：传递 properties
                System.currentTimeMillis(), // 修复：传递 timestamp
                commandName) {
            // 匿名内部类，可以添加特定的属性，如果需要
            // 例如，可以传递 args 到 properties
            {
                // setProperties(args); // 属性已在构造函数中传递
            }
        };
        // cmd.setId(String.valueOf(generateCommandId())); // ID 已在构造函数中设置
        return submitCommand(cmd);
    }
}