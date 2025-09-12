package source.hanger.core.extension.system;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import javax.sound.sampled.AudioFormat;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseExtension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.ByteBufUtils;
import source.hanger.processor.DeepFilterNetStreamProcessor;

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public class DeppFilterNetDenoiseExtension extends BaseExtension {

    private boolean enableDenoising;
    private DeepFilterNetStreamProcessor deepFilterNetStreamProcessor = null;

    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        enableDenoising = Optional.ofNullable(properties.get("enableDenoising"))
            .map(String::valueOf).map(Boolean::valueOf)
            .orElse(false);

        if (!enableDenoising) {
            return;
        }

        float attenLim = 100.0f; // 固定值
        String logLevel = "error"; // 固定值
        int ringBufferCapacity = 8192; // 固定值
        int listenerQueueCapacity = 500; // 固定值

        // 默认音频格式 (可从配置中获取)
        AudioFormat format = new AudioFormat(48000.0f, 16, 1, true, false);

        deepFilterNetStreamProcessor = new DeepFilterNetStreamProcessor(attenLim, logLevel,
            (bytes, offset, length) -> {
                // 创建并发送降噪后的 AudioFrameMessage
                AudioFrameMessage audioFrameMessage = AudioFrameMessage.createBuilder("pcm_frame")
                    .buf(ByteBufUtils.toByteBuf(bytes, offset, length))
                    .bytesPerSample(format.getSampleSizeInBits())
                    .numberOfChannel(format.getChannels())
                    .sampleRate((int)format.getSampleRate())
                    .build();
                env.sendAudioFrame(audioFrameMessage);
                //AudioFileWriter.DEFAULT.writeAudioFrame(ByteBufUtils.toByteBuffer(bytes, offset, length));
                log.trace("[{}] Denoised audio frame sent, length: {}", env.getExtensionName(), length);
            }, ringBufferCapacity, listenerQueueCapacity);

        log.info(
            "[{}] DeepFilterNetStreamProcessor configured with fixed parameters: attenLim: {}, logLevel: {}, "
                + "ringBufferCapacity: {}, listenerQueueCapacity: {}",
            env.getExtensionName(), attenLim, logLevel, ringBufferCapacity, listenerQueueCapacity);
    }

    @Override
    public void onStart(TenEnv env) {
        if (deepFilterNetStreamProcessor != null) {
            deepFilterNetStreamProcessor.start();
            log.info("[{}] DeepFilterNetStreamProcessor started.", env.getExtensionName());
        }
    }

    @Override
    public void onStop(TenEnv env) {
        if (deepFilterNetStreamProcessor != null) {
            deepFilterNetStreamProcessor.stop();
            log.info("[{}] DeepFilterNetStreamProcessor stopped.", env.getExtensionName());
        }
    }

    @Override
    public void onAudioFrame(TenEnv env, AudioFrameMessage audioFrame) {
        if (enableDenoising) {
            deepFilterNetStreamProcessor.processAudioFrame(ByteBufUtils.toByteBuffer(audioFrame.getBuf()));
            log.trace("[{}] Received and processed audio frame, length: {}", env.getExtensionName(),
                audioFrame.getBuf().readableBytes());
        } else {
            audioFrame.setDestLocs(new ArrayList<>());
            env.sendAudioFrame(audioFrame);
        }
    }
}
