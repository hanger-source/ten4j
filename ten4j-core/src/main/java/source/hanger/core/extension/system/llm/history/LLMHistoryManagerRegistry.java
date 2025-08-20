package source.hanger.core.extension.system.llm.history;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * LLM历史对话管理器的注册中心。
 * 通过 graphId 来管理和共享 LLMHistoryManager 实例。
 */
@Slf4j
public class LLMHistoryManagerRegistry {

    private static final LLMHistoryManagerRegistry INSTANCE = new LLMHistoryManagerRegistry();
    private final ConcurrentHashMap<String, LLMHistoryManager> registry = new ConcurrentHashMap<>();

    private LLMHistoryManagerRegistry() {
        // 私有构造函数，实现单例模式
    }

    public static LLMHistoryManagerRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 获取或创建指定 graphId 的 LLMHistoryManager 实例。
     * 如果已存在，则返回现有实例；否则，创建新实例并注册。
     *
     * @param graphId 图的唯一标识符
     * @param systemPromptSupplier 用于创建新 LLMHistoryManager 时的系统提示词 Supplier
     * @return 对应的 LLMHistoryManager 实例
     */
    public LLMHistoryManager getOrCreateHistoryManager(String graphId, Supplier<String> systemPromptSupplier) {
        return registry.computeIfAbsent(graphId, k -> {
            log.info("[LLMHistoryManagerRegistry] Creating new LLMHistoryManager for graphId: {}", graphId);
            return new LLMHistoryManager(systemPromptSupplier);
        });
    }

    /**
     * 移除指定 graphId 的 LLMHistoryManager 实例。
     * 通常在图销毁时调用，以释放资源。
     *
     * @param graphId 图的唯一标识符
     */
    public void removeHistoryManager(String graphId) {
        LLMHistoryManager removed = registry.remove(graphId);
        if (removed != null) {
            log.info("[LLMHistoryManagerRegistry] Removed LLMHistoryManager for graphId: {}", graphId);
        }
    }

    /**
     * 清空所有注册的 LLMHistoryManager 实例。
     * 仅用于测试或特殊清理场景。
     */
    public void clear() {
        log.warn("[LLMHistoryManagerRegistry] Clearing all LLMHistoryManager instances. This should generally only be used for testing.");
        registry.clear();
    }
}
