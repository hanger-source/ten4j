package source.hanger.core.extension.dashscope.component.poolobject;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import source.hanger.core.tenenv.TenEnv;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sambert SpeechSynthesizer 对象的对象池。
 */
@Slf4j
public class SambertTTSObjectPool {
    public static GenericObjectPool<SpeechSynthesizer> synthesizerPool;
    private static final Lock lock = new ReentrantLock();

    public static GenericObjectPool<SpeechSynthesizer> getInstance(TenEnv env) {
        lock.lock();
        try {
            if (synthesizerPool == null) {
                //int objectPoolSize = getObjectivePoolSize(env);
                int objectPoolSize = 5;
                SpeechSynthesizerObjectFactory speechSynthesizerObjectFactory = new SpeechSynthesizerObjectFactory();
                GenericObjectPoolConfig<SpeechSynthesizer> config =
                        new GenericObjectPoolConfig<>();
                config.setMaxTotal(objectPoolSize);
                config.setMaxIdle(objectPoolSize);
                config.setMinIdle(objectPoolSize);
                synthesizerPool = new GenericObjectPool<>(speechSynthesizerObjectFactory, config);
            }
        } finally {
            lock.unlock();
        }
        return synthesizerPool;
    }

    @Slf4j
    @AllArgsConstructor
    public static class SpeechSynthesizerObjectFactory implements PooledObjectFactory<SpeechSynthesizer> {

        @Override
        public PooledObject<SpeechSynthesizer> makeObject() throws Exception {
            log.info("创建一个新的 SpeechSynthesizer 实例.");
            return new DefaultPooledObject<>(new SpeechSynthesizer());
        }

        @Override
        public void destroyObject(PooledObject<SpeechSynthesizer> p) throws Exception {
            SpeechSynthesizer synthesizer = p.getObject();
            synthesizer.getSyncApi().close(1000, "bye");
            log.info("销毁 SpeechSynthesizer 实例.");
        }

        @Override
        public boolean validateObject(PooledObject<SpeechSynthesizer> p) {
            return p.getObject() != null;
        }

        @Override
        public void activateObject(PooledObject<SpeechSynthesizer> p) throws Exception {
        }

        @Override
        public void passivateObject(PooledObject<SpeechSynthesizer> p) throws Exception {
        }
    }
}
