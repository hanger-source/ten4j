package source.hanger.core.extension.base;

import java.util.Map;
import java.util.function.Consumer;

import io.reactivex.disposables.CompositeDisposable;
import lombok.extern.slf4j.Slf4j;
import net.fellbaum.jemoji.EmojiManager;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.flush.DefaultFlushOperationCoordinator;
import source.hanger.core.extension.component.flush.FlushOperationCoordinator;
import source.hanger.core.extension.component.output.MessageOutputSender;
import source.hanger.core.extension.component.state.ExtensionStateProvider;
import source.hanger.core.extension.component.stream.DefaultStreamPipelineChannel;
import source.hanger.core.extension.component.stream.StreamOutputBlockConsumer;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.extension.component.tts.TTSAudioOutputBlock;
import source.hanger.core.extension.component.tts.TTSStreamAdapter;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.ByteBufUtils;
import source.hanger.core.util.MessageUtils;
import source.hanger.core.util.SentenceProcessor;

import static org.apache.commons.lang3.StringUtils.*;
import static source.hanger.core.common.ExtensionConstants.CMD_IN_FLUSH;
import static source.hanger.core.common.ExtensionConstants.CMD_OUT_FLUSH;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;

@Slf4j
public abstract class BaseTTSExtension extends BaseExtension {

    private final CompositeDisposable disposables = new CompositeDisposable();
    protected FlushOperationCoordinator flushOperationCoordinator;
    protected StreamPipelineChannel<OutputBlock> streamPipelineChannel;
    protected TTSStreamAdapter ttsStreamAdapter;

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        this.streamPipelineChannel = createStreamPipelineChannel(createOutputBlockConsumer());
        log.info("[{}] 配置中，初始化 StreamPipelineChannel。", env.getExtensionName());
        this.ttsStreamAdapter = createTTSStreamAdapter(streamPipelineChannel);
        log.info("[{}] 配置中，初始化 TTSStreamAdapter。", env.getExtensionName());
        // 6. 初始化 FlushOperationCoordinator (由子类提供具体实现，或使用通用实现)
        this.flushOperationCoordinator = createFlushOperationCoordinator(extensionStateProvider,
            streamPipelineChannel, (currentEnv) -> {
                // LLMStreamAdapter 的 onCancelLLM 方法被调用
                ttsStreamAdapter.onCancelTTS(currentEnv);
            });
    }

    private FlushOperationCoordinator createFlushOperationCoordinator(ExtensionStateProvider extensionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel, Consumer<TenEnv> onCancelFlushCallback) {
        return new DefaultFlushOperationCoordinator(extensionStateProvider, streamPipelineChannel, onCancelFlushCallback);
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[{}] BaseTTSExtension 启动，初始化管道。", env.getExtensionName());
        EmojiManager.isEmoji("😄提前触发UnicodeInitialized");
        streamPipelineChannel.initPipeline(env);
    }

    @Override
    public void onDeinit(TenEnv env) {
        log.info("[{}] TTS扩展清理，停止连接.", env.getExtensionName());
        streamPipelineChannel.disposeCurrent();
        disposables.clear();
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        if (!isRunning()) {
            log.warn("[{}] Extension未运行，忽略 Command。", env.getExtensionName());
            return;
        }
        // 处理 CMD_FLUSH 命令
        if (CMD_IN_FLUSH.equals(command.getName())) {
            log.info("[{}] 收到 来自 {} CMD_FLUSH 命令，执行刷新操作并重置历史。", env.getExtensionName(),
                command.getSrcLoc().getExtensionName());
            flushOperationCoordinator.triggerFlush(env);
            env.sendCmd(GenericCommand.create(CMD_OUT_FLUSH, command.getId()));
            return;
        }
    }

    @Override
    public void onDataMessage(TenEnv env, DataMessage dataMessage) {
        if (!isRunning()) {
            log.warn("[{}] TTS Extension未运行，忽略语音转录", env.getExtensionName());
            return;
        }
        String inputText = dataMessage.getPropertyString(DATA_OUT_PROPERTY_TEXT).orElse("");
        // 使用 EmojiManager 过滤掉 inputText 中的 emoji
        String filteredInputText = EmojiManager.removeAllEmojis(inputText)
            // 移除换行符和空格
            .replace("\n", "").strip();

        // 【新增】过滤掉只包含标点符号的文本
        if (SentenceProcessor.isPureSymbols(filteredInputText)) {
            if (isNotBlank(filteredInputText)) {
                log.warn("[{}] Received ignored text for TTS, ignoring. text={}", env.getExtensionName(),
                    filteredInputText);
            }
            return;
        }

        log.info("[{}] Received TTS request for text: \"{}\"", env.getExtensionName(), filteredInputText);
        // 使用 QwenTtsClient 进行流式 TTS 调用
        ttsStreamAdapter.onRequestSpeechTranscription(env, filteredInputText, dataMessage);
    }

    protected void sendTtsError(TenEnv env, String messageId, MessageType messageType, String messageName,
        String errorMessage) {
        String finalMessageId = (messageId != null && !messageId.isEmpty()) ? messageId
            : MessageUtils.generateUniqueId();
        CommandResult errorResult = CommandResult.fail(finalMessageId, messageType, messageName, errorMessage);
        env.sendResult(errorResult);
        log.error("[{}] TTS Error [{}]: {}", env.getExtensionName(), messageName, errorMessage);
    }

    protected StreamPipelineChannel<OutputBlock> createStreamPipelineChannel(
        StreamOutputBlockConsumer<OutputBlock> streamOutputBlockConsumer) {
        return new DefaultStreamPipelineChannel(extensionStateProvider, streamOutputBlockConsumer);
    }

    protected abstract TTSStreamAdapter createTTSStreamAdapter(
        StreamPipelineChannel<OutputBlock> streamPipelineChannel);

    protected StreamOutputBlockConsumer<OutputBlock> createOutputBlockConsumer() {
        return (item, originalMessage, env) -> {
            if (item instanceof TTSAudioOutputBlock ttsAudioBlock) {
                // TTS 音频块
                log.info(
                    "[{}] TTSStream输出 (Audio): text={} originalId={} sampleRate={}, channels={}, "
                        + "sampleBytes={}",
                    env.getExtensionName(), originalMessage.getPropertyString(DATA_OUT_PROPERTY_TEXT).orElse(""),
                    originalMessage.getId(),
                    ttsAudioBlock.getSampleRate(), ttsAudioBlock.getChannels(), ttsAudioBlock.getSampleBytes());

                MessageOutputSender.sendAudioOutput(env, originalMessage, ByteBufUtils.fromByteBuffer(ttsAudioBlock.getData()),
                    ttsAudioBlock.getSampleRate(), ttsAudioBlock.getChannels(), ttsAudioBlock.getSampleBytes());
            } else {
                // 处理其他类型的 OutputBlock，如果需要
                log.warn("[{}] 收到未知类型的 OutputBlock: {}", env.getExtensionName(), item.getClass().getName());
            }
        };
    }
}
