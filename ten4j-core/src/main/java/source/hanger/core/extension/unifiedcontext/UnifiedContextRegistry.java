package source.hanger.core.extension.unifiedcontext;

import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.component.context.LLMContextManager;
import source.hanger.core.tenenv.TenEnv; 

@Slf4j
public class UnifiedContextRegistry {

    private static final ConcurrentHashMap<String, LLMContextManager<UnifiedMessage>> registry = new ConcurrentHashMap<>();

    // 修改方法签名，不再接收 uniqueSystemPromptSupplier
    public static LLMContextManager<UnifiedMessage> getOrCreateContextManager(TenEnv env) {
        String graphId = env.getGraphId();
        String commonSystemPrompt = env.getPropertyString("assistantMessage").orElse(null); // 从 TenEnv 获取公共系统提示
        return registry.computeIfAbsent(graphId, k -> {
            log.info("[UnifiedContextRegistry] Creating new UnifiedLLMContextManager for graphId: {}", k);
            return new UnifiedLLMContextManager(commonSystemPrompt); // 传入 commonSystemPrompt
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
    public static UnifiedLLMContextManager getContextManager(String graphId) { // 返回具体类型，方便调用 setCommonSystemPrompt
        return (UnifiedLLMContextManager) registry.get(graphId);
    }
}
