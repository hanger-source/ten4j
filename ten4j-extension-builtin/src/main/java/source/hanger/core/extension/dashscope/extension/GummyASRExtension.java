package source.hanger.core.extension.dashscope.extension;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseAsrExtension;
import source.hanger.core.extension.component.asr.ASRStreamAdapter;
import source.hanger.core.extension.dashscope.component.stream.GummyASRStreamAdapter;
import source.hanger.core.tenenv.TenEnv;

import static org.apache.commons.lang3.StringUtils.*;

@Slf4j
public class GummyASRExtension extends BaseAsrExtension {

    @Override
    protected ASRStreamAdapter createASRStreamAdapter() {
        return new GummyASRStreamAdapter(extensionStateProvider, streamPipelineChannel);
    }

    @Override
    protected boolean canDiscovery(TenEnv env) {
        String model = env.getPropertyString("model").orElse("");
        return containsIgnoreCase(model, "gummy");
    }
}
