package source.hanger.core.extension.dashscope.extension;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseAsrExtension;
import source.hanger.core.extension.component.asr.ASRStreamAdapter;
import source.hanger.core.extension.dashscope.component.stream.ParaformerASRStreamAdapter;

@Slf4j
public class ParaformerASRExtension extends BaseAsrExtension {

    @Override
    protected ASRStreamAdapter createASRStreamAdapter() {
        return new ParaformerASRStreamAdapter(extensionStateProvider, streamPipelineChannel);
    }
}
