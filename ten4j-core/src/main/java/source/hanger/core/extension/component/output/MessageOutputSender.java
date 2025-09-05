package source.hanger.core.extension.component.output;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import source.hanger.core.extension.component.asr.ASRTranscriptionOutputBlock;
import source.hanger.core.extension.component.stream.StreamOutputBlockConsumer;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;

import static java.util.Collections.*;
import static source.hanger.core.common.ExtensionConstants.ASR_DATA_OUT_NAME;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_END_OF_SEGMENT;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_IS_FINAL;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_ROLE;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;
import static source.hanger.core.common.ExtensionConstants.TEXT_DATA_OUT_NAME;

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public abstract class MessageOutputSender {
    public static void sendTextOutput(TenEnv env, Message originalMessage, String text,
        boolean endOfSegment) { // 使用 core 包的 Message
        try {
            DataMessage outputData = DataMessage.createBuilder(TEXT_DATA_OUT_NAME)
                .id("%s_%s_%d".formatted(originalMessage.getId(), TEXT_DATA_OUT_NAME, System.currentTimeMillis()))
                .property(DATA_OUT_PROPERTY_TEXT, text)
                .property(DATA_OUT_PROPERTY_ROLE, "assistant")
                .property(DATA_OUT_PROPERTY_END_OF_SEGMENT, endOfSegment)
                .property("extension_name", env.getExtensionName())
                .property("group_timestamp", originalMessage.getTimestamp())
                .build();
            env.sendMessage(outputData);
            LoggerFactory.getLogger(StreamOutputBlockConsumer.class)
                .debug("[{}] LLM文本输出发送成功: text={}, endOfSegment={}", env.getExtensionName(),
                    text, endOfSegment);
        } catch (Exception e) {
            LoggerFactory.getLogger(StreamOutputBlockConsumer.class)
                .error("[{}] LLM文本输出发送异常: {}", env.getExtensionName(), e.getMessage(), e);
        }
    }

    public static void sendAsrTranscriptionOutput(TenEnv env, ASRTranscriptionOutputBlock block) {
        try {
            DataMessage message = DataMessage.createBuilder(ASR_DATA_OUT_NAME)
                .id("%s_%s_%d".formatted(block.getRequestId(), ASR_DATA_OUT_NAME, System.currentTimeMillis()))
                .property(DATA_OUT_PROPERTY_ROLE, "user")
                .property("asr_request_id", block.getRequestId())
                .property(DATA_OUT_PROPERTY_TEXT, block.getText())
                .property(DATA_OUT_PROPERTY_IS_FINAL, block.isFinal())
                .property(DATA_OUT_PROPERTY_END_OF_SEGMENT, block.isFinal())
                .property("start_ms", block.getStartTime())
                .property("duration_ms", block.getDuration())
                .property("language", "zh-CN")
                .property("metadata", singletonMap("session_id", ""))
                .property("words", emptyList())
                .build();

            env.sendData(message);
            log.info("[{}] Sent ASR transcription: {}", env.getExtensionName(), block.getText());
        } catch (Exception e) {
            log.error("[{}] 发送ASR文本输出异常: {}", env.getExtensionName(), e.getMessage(), e);
        }
    }

    public static void sendAudioOutput(TenEnv env, Message originalMessage, ByteBuf audioData,
        int sampleRate, int bytesPerSample,
        int numberOfChannels) {
        try {
            AudioFrameMessage audioFrame = AudioFrameMessage.createBuilder("pcm_frame")
                .id("%s_%s_%d".formatted(originalMessage.getId(), "pcm_frame", System.currentTimeMillis()))
                .sampleRate(sampleRate)
                .bytesPerSample(bytesPerSample)
                .numberOfChannel(numberOfChannels)
                .samplesPerChannel(audioData.readableBytes() / (bytesPerSample * numberOfChannels))
                .buf(audioData)
                .property("audio_text", originalMessage.getPropertyString(DATA_OUT_PROPERTY_TEXT).orElse(""))
                // 取llm留下来的group_timestamp 也就是llm一组回复
                .property("group_timestamp", originalMessage.getPropertyLong("group_timestamp")
                    .orElseThrow(() -> new RuntimeException("group_timestamp not found")))
                .build();
            env.sendMessage(audioFrame);

            log.debug("[{}] 发送音频帧成功: size={}", env.getExtensionName(), audioData.readableBytes());
        } catch (Exception e) {
            log.error("[{}] 发送音频帧异常: ", env.getExtensionName(), e);
        }
    }
}
