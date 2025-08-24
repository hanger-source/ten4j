package source.hanger.core.extension.dashscope.component.poolobject;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import source.hanger.core.tenenv.TenEnv;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CosyVoice SpeechSynthesizer 对象的对象池。
 */
@Slf4j
public class CosyVoiceObjectPool {
    public static GenericObjectPool<SpeechSynthesizer> synthesizerPool;
    private static final Lock lock = new ReentrantLock();

    public static GenericObjectPool<SpeechSynthesizer> getInstance(TenEnv env) {
        lock.lock();
        try {
            if (synthesizerPool == null) {
                //int objectPoolSize = getObjectivePoolSize(env);
                int objectPoolSize = 5;

                String apiKey = env.getPropertyString("api_key").orElseThrow(() -> new IllegalStateException("No api key found"));
                String voiceName = env.getPropertyString("voice_name").orElseThrow(() -> new IllegalStateException("No voiceName found"));
                String model = env.getPropertyString("model").orElseThrow(() -> new IllegalStateException("No model found"));
                SpeechSynthesisAudioFormat format = SpeechSynthesisAudioFormat.PCM_24000HZ_MONO_16BIT; // 固定格式

                SpeechSynthesizerObjectFactory speechSynthesizerObjectFactory =
                        new SpeechSynthesizerObjectFactory(apiKey, model, voiceName, format);
                GenericObjectPoolConfig<SpeechSynthesizer> config =
                        new GenericObjectPoolConfig<>();
                config.setMaxTotal(objectPoolSize);
                config.setMaxIdle(objectPoolSize);
                config.setMinIdle(objectPoolSize);
                synthesizerPool =
                        new GenericObjectPool<>(speechSynthesizerObjectFactory, config);
            }
        } finally {
            lock.unlock();
        }
        return synthesizerPool;
    }
}
