package source.hanger.core.extension.component.tool;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.tool.LLMTool;

/**
 * 扩展工具注册中心。
 * 作为一个单例模式的注册中心，负责收集所有实现 LLMTool 接口的 Extension 提供的 LLMTool 实例。
 */
@Slf4j
public class ExtensionToolRegistry<T extends LLMTool> { // 引入泛型 T，约束为 LLMTool 的子类

    private static volatile ExtensionToolRegistry<?> instance; // 使用通配符泛型，以支持不同 T 的单例
    private final Map<String, T> allRegisteredTools = new ConcurrentHashMap<>(); // Map 的值类型改为泛型 T

    private ExtensionToolRegistry() {
        // 私有构造函数，确保单例
    }

    @SuppressWarnings("unchecked") // Suppress warnings for unchecked cast
    public static <U extends LLMTool> ExtensionToolRegistry<U> getInstance() {
        if (instance == null) {
            synchronized (ExtensionToolRegistry.class) {
                if (instance == null) {
                    instance = new ExtensionToolRegistry<>(); // 创建泛型实例
                }
            }
        }
        return (ExtensionToolRegistry<U>) instance; // 强制转换为请求的泛型类型
    }

    /**
     * 注册单个 LLM 工具。
     *
     * @param tool 要注册的 LLMTool 实例。
     */
    public void registerTool(T tool) { // 参数类型改为泛型 T
        if (tool == null || tool.getToolName() == null || tool.getToolName().isEmpty()) {
            log.warn("[{}] Attempted to register a null or unnamed tool.", ExtensionToolRegistry.class.getSimpleName());
            return;
        }
        if (allRegisteredTools.containsKey(tool.getToolName())) {
            log.warn("[{}] Tool with name '{}' already registered. Overwriting.", ExtensionToolRegistry.class.getSimpleName(), tool.getToolName());
        }
        allRegisteredTools.put(tool.getToolName(), tool);
        log.info("[{}] Registered tool: '{}'.", ExtensionToolRegistry.class.getSimpleName(), tool.getToolName());
    }

    /**
     * 注册一个 LLM 工具列表。
     *
     * @param tools 要注册的 LLMTool 实例列表。
     */
    public void registerTools(List<T> tools) { // 参数类型改为泛型 T
        if (tools == null || tools.isEmpty()) {
            log.debug("[{}] Attempted to register a null or empty tool list.", ExtensionToolRegistry.class.getSimpleName());
            return;
        }
        tools.forEach(this::registerTool);
    }

    /**
     * 根据工具名称获取 LLM 工具。
     *
     * @param toolName 工具的名称。
     * @return 对应的 LLMTool 实例，如果不存在则返回 null。
     */
    public T getTool(String toolName) { // 返回类型改为泛型 T
        return allRegisteredTools.get(toolName);
    }

    /**
     * 获取所有已注册的 LLM 工具列表。
     *
     * @return 所有已注册工具的不可修改列表。
     */
    public Collection<T> getAllTools() { // 返回类型改为泛型 T
        return Collections.unmodifiableCollection(allRegisteredTools.values());
    }

    /**
     * 清空所有已注册的工具。
     * 通常用于 Extension 销毁或测试场景。
     */
    public void clear() {
        allRegisteredTools.clear();
        log.info("[{}] All registered tools cleared.", ExtensionToolRegistry.class.getSimpleName());
    }
}

