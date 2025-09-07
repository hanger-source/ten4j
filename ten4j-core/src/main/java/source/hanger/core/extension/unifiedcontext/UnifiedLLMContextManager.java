package source.hanger.core.extension.unifiedcontext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier; // 新增导入

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import source.hanger.core.extension.component.context.LLMContextManager;

@Slf4j
public class UnifiedLLMContextManager implements LLMContextManager<UnifiedMessage> {

    private final List<UnifiedMessage> history = new CopyOnWriteArrayList<>();
    private String commonSystemPrompt; // 公共系统提示部分，由 UnifiedLLMContextManager 维护
    private int maxHistory = 20;

    public UnifiedLLMContextManager(String commonSystemPrompt) { // 构造函数接收 commonSystemPrompt
        this.commonSystemPrompt = commonSystemPrompt;
    }

    private void smartTruncateHistory() {
        if (history.size() <= maxHistory) {
            return;
        }

        int toRemoveCount = history.size() - maxHistory;
        List<UnifiedMessage> currentHistorySnapshot = new ArrayList<>(history);
        Set<String> removedAssistantToolCallIds = new HashSet<>();

        for (int i = 0; i < toRemoveCount; i++) {
            if (i < currentHistorySnapshot.size()) {
                UnifiedMessage msg = currentHistorySnapshot.get(i);
                if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null) {
                    for (UnifiedToolCall toolCall : msg.getToolCalls()) {
                        if (toolCall.getId() != null) {
                            removedAssistantToolCallIds.add(toolCall.getId());
                        }
                    }
                }
            }
        }

        List<UnifiedMessage> newHistory = new ArrayList<>();
        for (int i = toRemoveCount; i < currentHistorySnapshot.size(); i++) {
            UnifiedMessage msg = currentHistorySnapshot.get(i);
            if ("tool".equals(msg.getRole())) {
                String toolCallId = msg.getToolCallId();
                if (toolCallId != null && removedAssistantToolCallIds.contains(toolCallId)) {
                    log.debug("[UnifiedContext] Skipping tool message with removed tool_call_id: {}", toolCallId);
                    continue;
                }
            }
            newHistory.add(msg);
        }

        history.clear();
        history.addAll(newHistory);
        log.debug("[UnifiedContext] History truncated. Current size: {}", history.size());
    }

    private int findToolCallInsertIndex(String toolCallId) {
        for (int i = history.size() - 1; i >= 0; i--) {
            UnifiedMessage historyMsg = history.get(i);
            if ("assistant".equals(historyMsg.getRole()) && historyMsg.getToolCalls() != null) {
                for (UnifiedToolCall toolCall : historyMsg.getToolCalls()) {
                    if (toolCallId.equals(toolCall.getId())) {
                        return i + 1; // 插入到assistant消息的后面
                    }
                }
            }
        }
        return -1; // 未找到匹配的assistant消息
    }

    public List<UnifiedMessage> getMessagesForLLM(Supplier<String> uniqueSystemPromptSupplier) {
        return getMessagesForLLM(s -> {
            StringBuilder combinedSystemPrompt = new StringBuilder();
            if (StringUtils.isNoneEmpty(commonSystemPrompt)) {
                combinedSystemPrompt.append(commonSystemPrompt);
            }
            String currentUniqueSystemPrompt = uniqueSystemPromptSupplier.get(); // 从 Supplier 获取独特的 systemPrompt
            if (StringUtils.isNoneEmpty(currentUniqueSystemPrompt)) {
                combinedSystemPrompt.append("\n").append(currentUniqueSystemPrompt);
            }
            if (!combinedSystemPrompt.isEmpty()) {
                return UnifiedMessage.builder()
                    .role("system")
                    .text(combinedSystemPrompt.toString())
                    .build();
            }
            return null;
        });
    }

        // 修改 getMessagesForLLM 方法，接收 uniqueSystemPromptSupplier
    public List<UnifiedMessage> getMessagesForLLM(Function<String, UnifiedMessage> func) {
        List<UnifiedMessage> llmMessages = new ArrayList<>();
        UnifiedMessage systemUnifiedMessage = func.apply(commonSystemPrompt);
        if (systemUnifiedMessage != null) {
            llmMessages.add(systemUnifiedMessage);
        }
        llmMessages.addAll(history);
        return llmMessages;
    }

    @Override
    public List<UnifiedMessage> getMessagesForLLM() {
        return getMessagesForLLM(() -> "");
    }

    @Override
    public void onAssistantMsg(String content) {
        history.add(UnifiedMessage.builder().role("assistant").text(content).build());
        smartTruncateHistory();
        log.debug("[UnifiedContext] Added assistant string message to history. Current size: {}", history.size());
    }

    @Override
    public void onAssistantMsg(UnifiedMessage message) {
        history.add(message);
        smartTruncateHistory();
        log.debug("[UnifiedContext] Added assistant message to history. Current size: {}", history.size());
    }

    @Override
    public void onUserMsg(String content) {
        history.add(UnifiedMessage.builder().role("user").text(content).build());
        smartTruncateHistory();
        log.debug("[UnifiedContext] Added user string message to history. Current size: {}", history.size());
    }

    @Override
    public void onUserVideoMsg(String content, List<String> base64Images) {
        UnifiedMessage.UnifiedMessageBuilder builder = UnifiedMessage.builder().role("user");
        if (content != null && !content.isEmpty()) {
            builder.text(content);
        }
        if (base64Images != null) {
            builder.images(base64Images.stream().map("data:image/jpeg;base64,%s"::formatted).toList());
        }
        history.add(builder.build());
        smartTruncateHistory();
        log.debug("[UnifiedContext] Added user video message to history. Current size: {}", history.size());
    }

    @Override
    public void onToolCallMsg(UnifiedMessage message) {
        if (message.getToolCallId() != null) {
            int insertIndex = findToolCallInsertIndex(message.getToolCallId());
            if (insertIndex != -1) {
                history.add(insertIndex, message);
                log.debug("[UnifiedContext] Inserted tool call message with ID {} at index {}. Current size: {}", message.getToolCallId(), insertIndex, history.size());
                smartTruncateHistory();
            } else {
                log.warn("[UnifiedContext] Discarding tool call message with ID {} because no matching assistant message with tool_call_id was found.", message.getToolCallId());
            }
        } else {
            history.add(message);
            log.debug("[UnifiedContext] Added tool call message without ID to history. Current size: {}", history.size());
            smartTruncateHistory();
        }
    }

    @Override
    public void clearHistory() {
        history.clear();
        log.debug("[UnifiedContext] History cleared.");
    }

    @Override
    public void setMaxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
        smartTruncateHistory();
    }

    @Override
    public void onDestroy() {
        history.clear();
    }

    public void setCommonSystemPrompt(String commonSystemPrompt) { // 提供更新 commonSystemPrompt 的方法
        if (commonSystemPrompt != null && !commonSystemPrompt.isEmpty()) {
            if (this.commonSystemPrompt == null || !this.commonSystemPrompt.equals(commonSystemPrompt)) {
                log.info("[UnifiedContext] Updating common system assistantMessage. Old: \'{}\', New: \'{}\'", this.commonSystemPrompt, commonSystemPrompt);
                this.commonSystemPrompt = commonSystemPrompt;
            }
        }
    }
}
