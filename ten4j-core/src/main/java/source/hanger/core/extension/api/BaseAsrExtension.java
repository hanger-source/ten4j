package source.hanger.core.extension.api;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.disposables.CompositeDisposable;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.asr.ASRStreamAdapter;
import source.hanger.core.extension.component.asr.ASRTranscriptionOutputBlock;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.stream.DefaultStreamPipelineChannel;
import source.hanger.core.extension.component.stream.StreamOutputBlockConsumer;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.MessageType;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;

import static source.hanger.core.extension.component.output.MessageOutputSender.sendAsrTranscriptionOutput;

@Slf4j
public abstract class BaseAsrExtension extends BaseExtension {

    private final CompositeDisposable disposables = new CompositeDisposable(); // 管理所有 Disposable
    // 成员变量
    protected StreamPipelineChannel<OutputBlock> streamPipelineChannel;
    protected ASRStreamAdapter asrStreamAdapter;

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        // 初始化 StreamPipelineChannel (使用统一的状态提供者)
        this.streamPipelineChannel = createStreamPipelineChannel(createOutputBlockConsumer());
        log.info("[{}] 配置中，初始化 StreamPipelineChannel。", env.getExtensionName());
        // 初始化 ASRStreamAdapter (由子类提供具体实现)
        this.asrStreamAdapter = createASRStreamAdapter();
        log.info("[{}] 配置中，初始化 ASRStreamAdapter。", env.getExtensionName());
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[{}] BaseAsrExtension 启动，初始化管道。", env.getExtensionName());
        streamPipelineChannel.initPipeline(env);
        // 开始ASR流
        asrStreamAdapter.startASRStream(env); // 调用 ASRStreamAdapter 的启动方法
    }

    @Override
    public void onDeinit(TenEnv env) {
        log.info("[{}] ASR扩展清理，停止连接.", env.getExtensionName());
        streamPipelineChannel.disposeCurrent();
        disposables.clear(); // 清理所有定时器 Disposable
    }

    @Override
    public final void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        if (!isRunning()) {
            log.warn("[{}] ASR Extension未运行，忽略音频帧: frameId={}",
                env.getExtensionName(), audioFrame.getId());
            return;
        }
        asrStreamAdapter.onAudioFrame(env, audioFrame); // 将音频帧转发给 ASRStreamAdapter
    }

    protected void sendAsrError(TenEnv env, String messageId, MessageType messageType, String messageName,
        String errorMessage) {
        String finalMessageId = (messageId != null && !messageId.isEmpty()) ? messageId
            : MessageUtils.generateUniqueId();
        CommandResult errorResult = CommandResult.fail(finalMessageId, messageType, messageName, errorMessage);
        env.sendResult(errorResult);
        log.error("[{}] ASR Error [{}]: {}", env.getExtensionName(), messageName, errorMessage);
    }

    /**
     * 抽象方法：创建 StreamPipelineChannel 实例。
     */
    protected StreamPipelineChannel<OutputBlock> createStreamPipelineChannel(
        StreamOutputBlockConsumer<OutputBlock> streamOutputBlockConsumer) {
        return new DefaultStreamPipelineChannel(extensionStateProvider, streamOutputBlockConsumer);
    }

    /**
     * 抽象方法：创建 ASRStreamAdapter 实例。
     * 子类应返回 ASRStreamAdapter 的具体实现，例如 ParaformerASRStreamAdapter。
     */
    protected abstract ASRStreamAdapter createASRStreamAdapter();

    protected StreamOutputBlockConsumer<OutputBlock> createOutputBlockConsumer() {
        return (item, _, env) -> {
            if (item instanceof ASRTranscriptionOutputBlock asrTextBlock) {
                // ASR 文本块
                log.info("[{}] ASRStream输出 (Text): {}", env.getExtensionName(), asrTextBlock.getText());
                Map<String, Object> properties = new HashMap<>();
                sendAsrTranscriptionOutput(env, asrTextBlock);
            }
        };
    }

}
