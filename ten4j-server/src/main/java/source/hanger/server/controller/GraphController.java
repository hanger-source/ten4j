package source.hanger.server.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import source.hanger.core.graph.GraphConfig;
import source.hanger.core.graph.GraphLoader;
import source.hanger.core.graph.PredefinedGraphEntry;
import source.hanger.server.loader.ResourceLoader;
import source.hanger.server.pojo.resource.ModelOption;
import source.hanger.server.pojo.resource.VoiceOption;

import static org.apache.commons.collections4.CollectionUtils.*;

@Slf4j
@HttpRequestController(value = "/graphs")
public class GraphController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @HttpRequestMapping(path = "/", method = "GET")
    public String getGraphs(FullHttpRequest request) {
        ResourceLoader resourceLoader = ResourceLoader.getInstance();
        List<GraphInfo> graphs = new ArrayList<>();

        try {
            GraphConfig graphConfig = GraphLoader.loadPredefinedGraphsConfig();

            if (graphConfig != null && graphConfig.getPredefinedGraphs() != null) {
                for (PredefinedGraphEntry entry : graphConfig.getPredefinedGraphs()) {
                    if (entry != null && entry.getGraph() != null) {
                        String uuid = entry.getGraph().getGraphId();
                        String name = entry.getName();
                        boolean autoStart = entry.isAutoStart();
                        String docUrl = entry.getDocUrl();
                        Object metadata = entry.getMetadata();

                        List<VoiceOption> graphVoices = loadVoices(entry, resourceLoader);
                        List<ModelOption> graphModels = loadModels(entry, resourceLoader);

                        graphs.add(new GraphInfo(uuid, name, entry.getIndex(), autoStart, docUrl, metadata, graphVoices, graphModels));
                    } else {
                        log.warn("Invalid PredefinedGraphEntry found, skipping: {}", entry);
                    }
                }
            }

            return objectMapper.writeValueAsString(graphs.stream()
                .sorted(Comparator.comparing(GraphInfo::getIndex))
                .collect(Collectors.toList()));
        } catch (IOException e) {
            log.error("Error loading or serializing graphs: {}", e.getMessage(), e);
            return "Internal Server Error: %s".formatted(e.getMessage());
        }
    }

    @Getter
    @AllArgsConstructor
    private static class GraphInfo {
        private String uuid;
        private String name;
        private Integer index;
        private boolean autoStart;
        private String docUrl;
        private Object metadata;
        private List<VoiceOption> voices;
        private List<ModelOption> models;
    }

    private List<VoiceOption> loadVoices(PredefinedGraphEntry entry, ResourceLoader resourceLoader) {
        if (isEmpty(entry.getVoices())) {
            return new ArrayList<>();
        }
        return entry.getVoices().stream()
                .map(resourceLoader.getVoicesMap()::get)
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<ModelOption> loadModels(PredefinedGraphEntry entry, ResourceLoader resourceLoader) {
        if (isEmpty(entry.getModels())) {
            return new ArrayList<>();
        }
        return entry.getModels().stream()
                .map(resourceLoader.getModelsMap()::get)
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
