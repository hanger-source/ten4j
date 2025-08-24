package source.hanger.core.extension.dashscope.component.poolobject;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 * SpeechSynthesizer 的对象工厂，用于 Apache Commons Pool。
 */
@Slf4j
@AllArgsConstructor
public class SpeechSynthesizerObjectFactory implements PooledObjectFactory<SpeechSynthesizer> {

    private final String apiKey;
    private final String model;
    private final String voiceName;
    private final SpeechSynthesisAudioFormat format;

    @Override
    public PooledObject<SpeechSynthesizer> makeObject() throws Exception {
        log.info("创建一个新的 SpeechSynthesizer 实例. Model: {}, Voice: {}", model, voiceName);
        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
            .apiKey(apiKey)
            .model(model)
            .voice(voiceName)
            .format(format)
            .build();
        return new DefaultPooledObject<>(new SpeechSynthesizer(param, null));
    }

    @Override
    public void destroyObject(PooledObject<SpeechSynthesizer> p) throws Exception {
        SpeechSynthesizer synthesizer = p.getObject();
        synthesizer.getDuplexApi().close(1000, "bye");
        // 如果 SpeechSynthesizer 有 close 方法，可以在这里调用
        log.info("销毁 SpeechSynthesizer 实例.");
    }

    @Override
    public boolean validateObject(PooledObject<SpeechSynthesizer> p) {
        // 可以实现更复杂的验证逻辑，例如检查连接状态
        return p.getObject() != null;
    }

    @Override
    public void activateObject(PooledObject<SpeechSynthesizer> p) throws Exception {
        // 对象被借用时调用，如果需要重置状态可以在这里实现
    }

    @Override
    public void passivateObject(PooledObject<SpeechSynthesizer> p) throws Exception {
        // 对象归还到池中时调用，如果需要清理状态可以在这里实现
    }
}
