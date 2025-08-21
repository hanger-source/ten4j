package source.hanger.core.extension.component.output;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import source.hanger.core.common.ExtensionConstants;
import source.hanger.core.extension.component.asr.ASRTranscriptionOutputBlock;
import source.hanger.core.extension.component.stream.StreamOutputBlockConsumer;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import source.hanger.core.tenenv.TenEnv;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_END_OF_SEGMENT;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_ROLE;
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;
import static source.hanger.core.common.ExtensionConstants.ASR_DATA_OUT_NAME; // 新增
import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_IS_FINAL; // 新增

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public abstract class MessageOutputSender {
    public static void sendTextOutput(TenEnv env, Message originalMessage, String text,
        boolean endOfSegment) { // 使用 core 包的 Message
        try {
            DataMessage outputData = DataMessage.create(ExtensionConstants.TEXT_DATA_OUT_NAME);
            outputData.setId(originalMessage.getId()); // 使用原始消息的ID
            outputData.setProperty(DATA_OUT_PROPERTY_TEXT, text);
            outputData.setProperty(DATA_OUT_PROPERTY_ROLE, "assistant");
            outputData.setProperty(DATA_OUT_PROPERTY_END_OF_SEGMENT, endOfSegment);
            outputData.setProperty("extension_name", env.getExtensionName());
            outputData.setProperty("group_timestamp", originalMessage.getTimestamp());

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
            Map<String, Object> properties = new HashMap<>();
            properties.put(DATA_OUT_PROPERTY_TEXT, block.getText());
            properties.put(DATA_OUT_PROPERTY_IS_FINAL, block.isFinal());
            properties.put(DATA_OUT_PROPERTY_END_OF_SEGMENT, block.isFinal());
            properties.put("start_ms", block.getStartTime());
            properties.put("duration_ms", block.getDuration());
            properties.put("language", "zh-CN");
            properties.put("metadata", Collections.singletonMap("session_id", "")); // session_id 暂时为空
            properties.put("words", Collections.emptyList());

            DataMessage message = DataMessage.create(ASR_DATA_OUT_NAME);
            properties.put(DATA_OUT_PROPERTY_ROLE, "user");
            properties.put("asr_request_id", block.getRequestId());
            message.setProperties(properties);
            env.sendData(message);
            log.info("[{}] Sent ASR transcription: {}", env.getExtensionName(), block.getText());
        } catch (Exception e) {
            log.error("[{}] 发送ASR文本输出异常: {}", env.getExtensionName(), e.getMessage(), e);
        }
    }

    public static void sendAudioOutput(TenEnv env, Message originalMessage, byte[] audioData,
        int sampleRate, int bytesPerSample,
        int numberOfChannels) {
        try {
            AudioFrameMessage audioFrame = AudioFrameMessage.create("pcm_frame");
            audioFrame.setId(originalMessage.getId()); // 使用原始消息的ID
            audioFrame.setSampleRate(sampleRate);
            audioFrame.setBytesPerSample(bytesPerSample);
            audioFrame.setNumberOfChannel(numberOfChannels);
            audioFrame.setSamplesPerChannel(audioData.length / (bytesPerSample * numberOfChannels));
            audioFrame.setBuf(audioData);
            audioFrame.setType(MessageType.AUDIO_FRAME);
            // 取llm留下来的group_timestamp 也就是llm一组回复
            audioFrame.setProperty("audio_text", originalMessage.getProperty("text"));
            audioFrame.setProperty("group_timestamp", originalMessage.getProperty("group_timestamp"));
            env.sendMessage(audioFrame);
            log.debug("[{}] 发送音频帧成功: size={}", env.getExtensionName(), audioData.length);
        } catch (Exception e) {
            log.error("[{}] 发送音频帧异常: ", env.getExtensionName(), e);
        }
    }
}
