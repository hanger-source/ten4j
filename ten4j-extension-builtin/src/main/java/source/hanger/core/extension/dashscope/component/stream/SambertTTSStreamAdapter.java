package source.hanger.core.extension.dashscope.component.stream;

import java.nio.ByteBuffer;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import org.apache.commons.pool2.impl.GenericObjectPool;
import source.hanger.core.extension.component.tts.BaseTTSStreamAdapter;
import source.hanger.core.extension.component.tts.TTSAudioOutputBlock;
import source.hanger.core.extension.dashscope.component.poolobject.SambertTTSObjectPool;
import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;

import static source.hanger.core.common.ExtensionConstants.DATA_OUT_PROPERTY_TEXT;

/**
 * Sambert TTS 流适配器，负责与 DashScope Sambert TTS 服务进行交互并处理流式响应。
 */
@Slf4j
public class SambertTTSStreamAdapter extends BaseTTSStreamAdapter<SpeechSynthesisResult> {

    private GenericObjectPool<SpeechSynthesizer> pool;

    public SambertTTSStreamAdapter(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        super(interruptionStateProvider, streamPipelineChannel);
    }

    @Override
    public void onStart(TenEnv env) {
        pool = SambertTTSObjectPool.getInstance(env);
    }

    @Override
    public void onStop(TenEnv env) {
        pool.clear();
    }

    @Override
    protected Flowable<SpeechSynthesisResult> getRawTtsFlowable(TenEnv env, String text) {

        String apiKey = env.getPropertyString("api_key").orElseThrow(
            () -> new IllegalStateException("No api key found"));
        String voiceName = env.getPropertyString("voice_name").orElseThrow(
            () -> new IllegalStateException("No voiceName found"));
        // Sambert uses voiceName as model, no separate model property

        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
            .apiKey(apiKey)
            .model(voiceName) // Sambert uses voiceName as model
            .text(text)
            .format(SpeechSynthesisAudioFormat.PCM)
            .sampleRate(16000)
            .build();

        StopWatch stopWatch = StopWatch.createStarted();
        return Flowable.using(
            () -> { // resourceSupplier: 借用实例
                SpeechSynthesizer s = pool.borrowObject();
                log.debug("[{}] SpeechSynthesizer 实例参数更新完毕. channelId={} text={}",
                    env.getExtensionName(), streamPipelineChannel.uuid(), text);
                return s;
            },
            s -> { // flowableSupplier
                return s.streamCall(param)
                    .subscribeOn(Schedulers.io()) // 确保 TTS SDK 调用在 IO 线程进行
                    .doOnNext(result -> {
                        if (!stopWatch.isStopped()) {
                            stopWatch.stop();
                            log.info("[{}] DashScope Sambert channelId={} 音频首帧输出 elapsed_time={}ms text={}",
                                env.getExtensionName(), streamPipelineChannel.uuid(), stopWatch.getTime(), text);
                        }
                    }).doOnTerminate(() -> {
                        if (!stopWatch.isStopped()) {
                            stopWatch.stop();
                        }
                    })
                    .doOnError(throwable -> {
                        s.getSyncApi().close(1000, "bye");
                        log.error("[{}] 调用 DashScope Sambert TTS API 错误. channelId={} text={}",
                            env.getExtensionName(), streamPipelineChannel.uuid(), text, throwable);
                    });
            },
            s -> { // disposeResource: 释放资源 (归还到池中)
                if (interruptionStateProvider.isInterrupted()) {
                    s.getSyncApi().close(1000, "bye");
                    log.info("[{}] 检测到中断，关闭连接. channelId={} text={}",
                        env.getExtensionName(), streamPipelineChannel.uuid(), text);
                }
                pool.returnObject(s);
                log.info("[{}] 归还 SpeechSynthesizer 实例到对象池. channelId={} text={}",
                    env.getExtensionName(), streamPipelineChannel.uuid(), text);
            }
        );
    }

    @Override
    protected Flowable<PipelinePacket<OutputBlock>> transformSingleTTSResult(SpeechSynthesisResult result, Message originalMessage, TenEnv env) {
        ByteBuffer audioFrame = result.getAudioFrame();
        if (audioFrame != null && audioFrame.capacity() > 0 && audioFrame.position() == audioFrame.capacity()) {
            // streamCall(param) SDK 内部write 导致pos=0, remaining=0
            // channel.write(result.getAudioFrame());
            // 暂时特殊处理
            audioFrame.rewind();
        }
        if (audioFrame != null && audioFrame.remaining() > 0) {
            TTSAudioOutputBlock block = new TTSAudioOutputBlock(audioFrame, originalMessage.getId(), 16000, 2, 1);
            log.info("[{}] TTS原始流处理开始. text={} originalId: {}", env.getExtensionName(),
                originalMessage.getPropertyString(DATA_OUT_PROPERTY_TEXT).orElse(""),
                originalMessage.getId());
            return Flowable.just(new PipelinePacket<>(block, originalMessage));
        } else {
            return Flowable.empty();
        }
    }
}
