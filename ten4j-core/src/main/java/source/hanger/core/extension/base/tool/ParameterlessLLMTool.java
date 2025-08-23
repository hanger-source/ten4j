package source.hanger.core.extension.base.tool;

import static java.util.Collections.*;

/**
 * @author fuhangbo.hanger.uhfun
 **/
public interface ParameterlessLLMTool extends LLMTool {
    @Override
    default LLMToolMetadata getToolMetadata() {
        return LLMToolMetadata.builder().name(getToolName()).description(getDescription())
            .parameters(emptyList()).build();
    }

    String getDescription();

}
