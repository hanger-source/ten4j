package source.hanger.core.extension.internal.datetime;

import source.hanger.core.extension.system.tool.LLMTool;
import source.hanger.core.extension.system.tool.LLMToolResult;
import source.hanger.core.extension.system.tool.ToolMetadata;
import source.hanger.core.tenenv.TenEnv;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

public class GetCurrentDateTimeTool implements LLMTool {

    @Override
    public ToolMetadata getToolMetadata() {
        return ToolMetadata.builder()
                .name("get_current_datetime")
                .description("获取当前最新的日期和时间，精确到秒。")
                .parameters(Collections.emptyList())
                .build();
    }

    @Override
    public LLMToolResult runTool(TenEnv env, Map<String, Object> args) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDateTime = now.format(formatter);
        return new LLMToolResult.LLMResult("当前日期和时间是: %s".formatted(formattedDateTime));
    }

    @Override
    public String getToolName() {
        return "get_current_datetime";
    }
}
