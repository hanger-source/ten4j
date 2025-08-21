package source.hanger.core.extension.component.llm;

/**
 * 通用工具调用片段。
 * 定义一个与特定 LLM 供应商解耦的工具调用片段结构。
 *
 * @param name          Getters and setters 工具名称
 * @param argumentsJson 当前片段的参数 (JSON 字符串)
 * @param id            工具调用 ID (用于流式聚合)
 */
public record ToolCallOutputFragment(String name, String argumentsJson, String id) {
}
