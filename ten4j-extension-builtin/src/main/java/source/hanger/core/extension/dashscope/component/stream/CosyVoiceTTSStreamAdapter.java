package source.hanger.core.extension.dashscope.component.stream;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.exception.NoApiKeyException;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.common.PipelinePacket;
import source.hanger.core.extension.component.flush.InterruptionStateProvider;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.extension.component.tts.BaseTTSStreamAdapter;
import source.hanger.core.extension.component.tts.TTSAudioOutputBlock;
import source.hanger.core.extension.dashscope.component.poolobject.CosyVoiceObjectPool;
import source.hanger.core.message.Message;
import source.hanger.core.tenenv.TenEnv;

/**
 * Cosy Voice TTS 流适配器，负责与 DashScope Cosy Voice TTS 服务进行交互并处理流式响应。
 */
@Slf4j
public class CosyVoiceTTSStreamAdapter extends BaseTTSStreamAdapter<SpeechSynthesisResult> {

    public CosyVoiceTTSStreamAdapter(
        InterruptionStateProvider interruptionStateProvider,
        StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        super(interruptionStateProvider, streamPipelineChannel);
    }

    @Override
    protected Flowable<SpeechSynthesisResult> getRawTtsFlowable(TenEnv env, String text) {
        GenericObjectPool<SpeechSynthesizer> pool = CosyVoiceObjectPool.getInstance(env);
        return Flowable.using(
            // resourceSupplier: 获取资源
            () -> {
                SpeechSynthesizer synthesizer = pool.borrowObject();
                log.debug("[{}] 从对象池借用 SpeechSynthesizer 实例。", env.getExtensionName());
                return synthesizer;
            },
            // flowableSupplier: 使用资源创建 Flowable
            synthesizer -> {
                try {
                    return synthesizer.callAsFlowable(text);
                } catch (NoApiKeyException e) {
                    log.error("[{}] 调用 DashScope Cosy Voice TTS API 出现错误: {}", env.getExtensionName(), e.getMessage(), e);
                    return Flowable.error(e);
                }
            },
            // disposeResource: 释放资源 (归还到池中)
            synthesizer -> {
                try {
                    pool.returnObject(synthesizer);
                    log.debug("[{}] 归还 SpeechSynthesizer 实例到对象池。", env.getExtensionName());
                } catch (Exception e) {
                    log.error("[{}] 归还 SpeechSynthesizer 到对象池失败: {}", env.getExtensionName(), e.getMessage(), e);
                }
            }
        );
    }

    @Override
    protected Flowable<PipelinePacket<OutputBlock>> transformSingleTTSResult(SpeechSynthesisResult result, Message originalMessage, TenEnv env) {
        if (result.getAudioFrame() != null && result.getAudioFrame().capacity() > 0) {
            byte[] audioData = new byte[result.getAudioFrame().capacity()];
            result.getAudioFrame().get(audioData); // 将 ByteBuffer 转换为 byte[]
            TTSAudioOutputBlock block = new TTSAudioOutputBlock(audioData, originalMessage.getId(), 24000, 2, 1); // 假设采样率等信息
            log.info("[{}] TTS原始流处理开始. 原始消息ID: {}", env.getExtensionName(), originalMessage.getId()); // 修改这里
            return Flowable.just(new PipelinePacket<>(block, originalMessage));
        } else {
            return Flowable.empty();
        }
    }
}
