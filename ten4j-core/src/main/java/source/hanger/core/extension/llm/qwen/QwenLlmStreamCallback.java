package source.hanger.core.extension.llm.qwen;

import java.util.List;

/**
 * 定义一个回调接口，用于处理流式输出的每个文本块。
 */
public interface QwenLlmStreamCallback {
    void onTextReceived(String text);

    void onComplete(String totalContent);

    void onError(Throwable t);
}