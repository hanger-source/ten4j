package source.hanger.core.extension.dashscope.client.asr;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.api.BaseAsrExtension;
import source.hanger.core.extension.component.asr.ASRStreamAdapter;

@Slf4j
public class ParaformerASRExtension extends BaseAsrExtension {

    @Override
    protected ASRStreamAdapter createASRStreamAdapter() {
        return new ParaformerASRStreamAdapter(extensionStateProvider, streamPipelineChannel);
    }
}
