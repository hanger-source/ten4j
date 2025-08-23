package source.hanger.core.extension.dashscope.component.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.tools.ToolCallFunction;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.extension.unifiedcontext.UnifiedContextRegistry;
import source.hanger.core.extension.unifiedcontext.UnifiedMessage;
import source.hanger.core.extension.unifiedcontext.UnifiedToolCall;
import source.hanger.core.tenenv.TenEnv;

/**
 * @author fuhangbo.hanger.uhfun
 **/
/**
 * LLM 上下文管理器接口的实现类。
 * 负责将 LLM 特有消息转换为统一消息，并转嫁给统一上下文管理器。
 * 本类不再管理历史，仅作为 LLM 特有消息与统一消息之间的适配层。
 */
@Slf4j
public class QwenMultiModalLLMContextManager
    implements LLMContextManager<MultiModalMessage> {
    private final LLMContextManager<UnifiedMessage> unifiedContextManager;

    public QwenMultiModalLLMContextManager(TenEnv env, Supplier<String> systemPromptSupplier) {
        this.unifiedContextManager = UnifiedContextRegistry.getOrCreateContextManager(env, systemPromptSupplier);
    }

    // 辅助方法：将 DashScope MultiModalMessage 转换为 UnifiedMessage
    private UnifiedMessage toUnifiedMessage(MultiModalMessage qwenMultiModalMessage) {
        UnifiedMessage.UnifiedMessageBuilder builder = UnifiedMessage.builder();
        builder.role(qwenMultiModalMessage.getRole());
        if (qwenMultiModalMessage.getContent() != null) {
            for (Map<String, Object> contentPart : qwenMultiModalMessage.getContent()) {
                if (contentPart.containsKey("text")) {
                    builder.text((String)contentPart.get("text"));
                } else if (contentPart.containsKey("image")) {
                    builder.images((String)contentPart.get("image"));
                }
            }
        }
        if (qwenMultiModalMessage.getToolCalls() != null && !qwenMultiModalMessage.getToolCalls().isEmpty()) {
            builder.toolCalls(qwenMultiModalMessage.getToolCalls().stream()
                .filter(ToolCallFunction.class::isInstance)
                .map(ToolCallFunction.class::cast)
                .map(tc -> UnifiedToolCall.builder()
                    .id(tc.getId())
                    .functionName(tc.getFunction().getName())
                    .functionArguments(tc.getFunction().getArguments())
                    .build())
                .collect(Collectors.toList()));
        }
        builder.toolCallId(qwenMultiModalMessage.getToolCallId());
        return builder.build();
    }

    // 辅助方法：将 UnifiedMessage 转换为 DashScope MultiModalMessage
    private MultiModalMessage fromUnifiedMessage(UnifiedMessage unifiedMessage) {
        MultiModalMessage.MultiModalMessageBuilder builder = MultiModalMessage.builder();
        builder.role(unifiedMessage.getRole());
        List<Map<String, Object>> contentList = new ArrayList<>();
        if (unifiedMessage.getText() != null) {
            contentList.add(Map.of("text", unifiedMessage.getText()));
        }
        if (unifiedMessage.getImages() != null) {
            for (String image : unifiedMessage.getImages()) {
                contentList.add(Map.of("image", image));
            }
        }
        builder.content(contentList);

        if (unifiedMessage.getToolCalls() != null && !unifiedMessage.getToolCalls().isEmpty()) {
            builder.toolCalls(unifiedMessage.getToolCalls().stream()
                .map(utc -> {
                    ToolCallFunction toolCallFunction = new ToolCallFunction();
                    toolCallFunction.setId(utc.getId());
                    ToolCallFunction.CallFunction callFunction = toolCallFunction.new CallFunction();
                    callFunction.setName(utc.getFunctionName());
                    // 将 Map<String, Object> 转换为 JSON 字符串
                    callFunction.setArguments(utc.getFunctionArguments());
                    toolCallFunction.setFunction(callFunction);
                    return toolCallFunction;
                })
                .collect(Collectors.toList()));
        }
        builder.toolCallId(unifiedMessage.getToolCallId());
        return builder.build();
    }

    @Override
    public List<MultiModalMessage> getMessagesForLLM() {
        return unifiedContextManager.getMessagesForLLM().stream()
            .map(this::fromUnifiedMessage)
            .collect(Collectors.toList());
    }

    @Override
    public void onAssistantMsg(String content) {
        unifiedContextManager.onAssistantMsg(content);
    }

    @Override
    public void onAssistantMsg(MultiModalMessage message) {
        unifiedContextManager.onAssistantMsg(toUnifiedMessage(message));
    }

    @Override
    public void onUserMsg(String content) {
        unifiedContextManager.onUserMsg(content);
    }

    @Override
    public void onUserVideoMsg(String content, List<String> base64Images) {
        unifiedContextManager.onUserVideoMsg(content, base64Images);
    }

    @Override
    public void onToolCallMsg(MultiModalMessage message) {
        unifiedContextManager.onToolCallMsg(toUnifiedMessage(message));
    }

    @Override
    public void clearHistory() {
        unifiedContextManager.clearHistory();
    }

    @Override
    public void setMaxHistory(int maxHistory) {
        unifiedContextManager.setMaxHistory(maxHistory);
    }

    @Override
    public void onDestroy() {
        unifiedContextManager.onDestroy();
    }
}
