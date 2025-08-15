package source.hanger.server.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.graph.GraphConfig;
import source.hanger.core.graph.GraphLoader;
import source.hanger.core.graph.PredefinedGraphEntry;

@Slf4j
@HttpRequestController(value = "/graphs")
public class GraphController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @HttpRequestMapping(path = "/", method = "GET")
    public String getGraphs(FullHttpRequest request) {
        List<GraphInfo> graphs = new ArrayList<>();

        try {
            // 调用 GraphLoader 加载预定义图配置
            GraphConfig graphConfig = GraphLoader.loadPredefinedGraphsConfig();

            if (graphConfig != null && graphConfig.getPredefinedGraphs() != null) {
                for (PredefinedGraphEntry entry : graphConfig.getPredefinedGraphs()) {
                    if (entry != null && entry.getGraph() != null) {
                        // 确保 uuid 存在，因为 GraphLoader 已经处理了生成随机 uuid 的逻辑
                        String uuid = entry.getGraph().getGraphId();
                        String name = entry.getName();
                        boolean autoStart = entry.isAutoStart();

                        graphs.add(new GraphInfo(uuid, name, autoStart));
                    } else {
                        log.warn("Invalid PredefinedGraphEntry found, skipping: {}", entry);
                    }
                }
            }

            return objectMapper.writeValueAsString(graphs);
        } catch (IOException e) {
            log.error("Error loading or serializing graphs: {}", e.getMessage(), e);
            return "Internal Server Error: %s".formatted(e.getMessage());
        }
    }

    // 内部类，用于表示图的信息（与前端期望的结构一致）
    @Getter
    @AllArgsConstructor
    private static class GraphInfo {
        // Getter 方法（Jackson 序列化需要）
        public String uuid;
        public String name;
        public boolean autoStart;
    }
}
