package source.hanger.core.extension.dashscope.extension;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseTTSExtension;
import source.hanger.core.extension.component.common.OutputBlock;
import source.hanger.core.extension.component.stream.StreamPipelineChannel;
import source.hanger.core.extension.component.tts.TTSStreamAdapter;
import source.hanger.core.extension.dashscope.component.stream.QwenTTSStreamAdapter;
import source.hanger.core.tenenv.TenEnv;

/**
 * Qwen TTS 扩展。
 */
@Slf4j
public class QwenTTSExtension extends BaseTTSExtension {


    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        super.onExtensionConfigure(env, properties);
        log.info("[{}] QwenTTSExtension 配置完成。", env.getExtensionName());
    }

    @Override
    protected TTSStreamAdapter createTTSStreamAdapter(StreamPipelineChannel<OutputBlock> streamPipelineChannel) {
        return new QwenTTSStreamAdapter(extensionStateProvider, streamPipelineChannel);
    }
}