package source.hanger.core.extension.internal.tool.datetime;

import java.util.List;

import source.hanger.core.extension.base.BaseToolExtension;
import source.hanger.core.extension.base.tool.LLMTool;
import source.hanger.core.tenenv.TenEnv;

public class GetCurrentDateTimeExtension extends BaseToolExtension {

    @Override
    protected List<LLMTool> initTools(TenEnv env) {
        return List.of(new GetCurrentDateTimeTool());
    }
}
