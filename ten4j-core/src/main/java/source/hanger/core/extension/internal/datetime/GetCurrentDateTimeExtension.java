package source.hanger.core.extension.internal.datetime;

import source.hanger.core.extension.system.tool.BaseLLMToolExtension;
import source.hanger.core.extension.system.tool.LLMTool;
import source.hanger.core.tenenv.TenEnv;

import java.util.Collections;
import java.util.List;

public class GetCurrentDateTimeExtension extends BaseLLMToolExtension {

    public GetCurrentDateTimeExtension() {
        // 扩展名称，通常与 extension.json 中的 name 字段一致
        // 父类 BaseExtension 默认构造函数，不需要传入 name
        super();
    }

    @Override
    protected List<LLMTool> getTools(TenEnv env) {
        return Collections.singletonList(new GetCurrentDateTimeTool());
    }
}
