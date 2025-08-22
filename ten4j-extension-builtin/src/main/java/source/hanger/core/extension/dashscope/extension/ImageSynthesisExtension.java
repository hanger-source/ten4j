package source.hanger.core.extension.dashscope.extension;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.BaseLLMToolExtension;
import source.hanger.core.extension.base.tool.LLMTool;
import source.hanger.core.extension.dashscope.tool.ImageSynthesisTool;
import source.hanger.core.tenenv.TenEnv;

/*
 * ImageSynthesisExtension 是一个 LLM 工具扩展，用于注册图片合成工具。
 */
@Slf4j
public class ImageSynthesisExtension extends BaseLLMToolExtension {

    @Override
    protected List<LLMTool> initTools(TenEnv env) {
        return List.of(new ImageSynthesisTool());
    }

    @Override
    public void onInit(TenEnv tenEnv) {
        super.onInit(tenEnv);
        log.info("[{}] ImageSynthesisExtension 初始化完成。", tenEnv.getExtensionName());
    }
}
