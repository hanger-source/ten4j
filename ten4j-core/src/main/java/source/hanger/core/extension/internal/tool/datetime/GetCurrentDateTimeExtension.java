package source.hanger.core.extension.internal.tool.datetime;

import java.util.List;

import source.hanger.core.extension.base.BaseLLMToolExtension;
import source.hanger.core.extension.base.tool.LLMTool;
import source.hanger.core.tenenv.TenEnv;

public class GetCurrentDateTimeExtension extends BaseLLMToolExtension {

    @Override
    protected List<LLMTool> initTools(TenEnv env) {
        return List.of(new GetCurrentDateTimeTool());
    }
}
