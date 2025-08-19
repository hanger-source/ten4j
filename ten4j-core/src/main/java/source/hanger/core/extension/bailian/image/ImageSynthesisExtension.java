package source.hanger.core.extension.bailian.image;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.system.tool.BaseLLMToolExtension;
import source.hanger.core.extension.system.tool.LLMTool;
import source.hanger.core.tenenv.TenEnv;

import java.util.Collections;
import java.util.List;

/**
 * ImageSynthesisExtension 是一个 LLM 工具扩展，用于注册图片合成工具。
 */
@Slf4j
public class ImageSynthesisExtension extends BaseLLMToolExtension {

    public ImageSynthesisExtension() {
        super(); // 调用 BaseExtension 的无参构造函数
    }

    @Override
    protected List<LLMTool> getTools(TenEnv tenEnv) {
        log.info("[{}] ImageSynthesisExtension 返回注册的工具。", tenEnv.getExtensionName());
        return Collections.singletonList(new ImageSynthesisTool());
    }

    @Override
    public void onInit(TenEnv tenEnv) {
        super.onInit(tenEnv);
        log.info("[{}] ImageSynthesisExtension 初始化完成。", tenEnv.getExtensionName());
    }
}
