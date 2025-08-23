package source.hanger.core.extension.unifiedcontext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.tenenv.TenEnv; 

@Slf4j
public class UnifiedContextRegistry {

    private static final ConcurrentHashMap<String, LLMContextManager<UnifiedMessage>> registry = new ConcurrentHashMap<>();

    // 修改方法签名，接收 TenEnv 和 Supplier<String> initialSystemPromptSupplier
    public static LLMContextManager<UnifiedMessage> getOrCreateContextManager(TenEnv env, Supplier<String> initialSystemPromptSupplier) {
        String graphId = env.getGraphId();
        return registry.computeIfAbsent(graphId, k -> {
            log.info("[UnifiedContextRegistry] Creating new UnifiedLLMContextManager for graphId: {}", k);
            return new UnifiedLLMContextManager(initialSystemPromptSupplier.get()); // 使用 Supplier 提供初始 systemPrompt
        });
    }

    public static void removeContextManager(String graphId) {
        LLMContextManager<UnifiedMessage> manager = registry.remove(graphId);
        if (manager != null) {
            manager.onDestroy();
            log.info("[UnifiedContextRegistry] Removed UnifiedLLMContextManager for graphId: {}", graphId);
        }
    }

    // 新增一个方法，用于在需要更新 systemPrompt 时获取 UnifiedLLMContextManager 实例
    public static LLMContextManager<UnifiedMessage> getContextManager(String graphId) {
        return registry.get(graphId);
    }
}
