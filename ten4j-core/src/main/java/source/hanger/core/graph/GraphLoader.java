package source.hanger.core.graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.util.ExpressionResolver;
import source.hanger.core.util.IdGenerator;
import source.hanger.core.util.ResourceUtils;

@Slf4j
public class GraphLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    /**
     * 从JSON字符串加载图配置。
     *
     * @param jsonString JSON格式的图配置字符串
     * @return 解析后的GraphConfig对象
     * @throws JsonProcessingException 如果JSON解析失败
     */
    public static GraphConfig loadGraphConfigFromJson(String jsonString) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(jsonString, GraphConfig.class);
    }

    /**
     * 从JSON字符串加载图定义。
     *
     * @param jsonString JSON格式的图定义字符串
     * @return 解析后的GraphDefinition对象
     * @throws JsonProcessingException 如果JSON解析失败
     */
    public static GraphDefinition loadGraphDefinitionFromJson(String jsonString,
            Map<String, Object> externalProperties)
            throws JsonProcessingException {
        GraphDefinition graphDefinition = OBJECT_MAPPER.readValue(jsonString, GraphDefinition.class);

        // Resolve properties at the graph definition level
        if (graphDefinition.getProperty() != null) {
            graphDefinition.setProperty((Map<String, Object>) resolveGraphProperties(graphDefinition.getProperty(),
                    externalProperties));
        }

        // Resolve properties within each node definition
        if (graphDefinition.getNodes() != null) {
            graphDefinition.getNodes().forEach(node -> {
                if (node.getProperty() != null) {
                    node.setProperty(
                            (Map<String, Object>) resolveGraphProperties(node.getProperty(), externalProperties));
                }
            });
        }

        return graphDefinition;
    }

    /**
     * 从文件加载图配置。
     *
     * @param filePath 图配置文件的路径
     * @return 解析后的GraphConfig对象
     * @throws IOException 如果文件读取或JSON解析失败
     */
    public static GraphConfig loadGraphConfigFromFile(String filePath) throws IOException {
        return OBJECT_MAPPER.readValue(new File(filePath), GraphConfig.class);
    }

    public static GraphConfig loadGraphConfigFromClassPath(String classPath) throws IOException {
        InputStream inputStream = GraphLoader.class.getClassLoader().getResourceAsStream(classPath);
        if (inputStream == null) {
            throw new FileNotFoundException("Graph config file not found: %s".formatted(classPath));
        }
        return OBJECT_MAPPER.readValue(inputStream, GraphConfig.class);
    }

    /**
     * 将GraphConfig对象转换为JSON字符串。
     *
     * @param graphConfig GraphConfig对象
     * @return JSON格式的图配置字符串
     * @throws JsonProcessingException 如果JSON序列化失败
     */
    public static String toJson(GraphConfig graphConfig) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(graphConfig);
    }

    private static Object resolveGraphProperties(Object value, Map<String, Object> externalProperties) {
        return ExpressionResolver.resolveProperties(value, externalProperties);
    }

    /**
     * 从 classpath 的 resources/graph 目录下加载所有预定义的图配置。
     *
     * @return 包含所有预定义图条目的 GraphConfig 对象
     * @throws IOException 如果读取或解析文件失败
     */
    public static GraphConfig loadPredefinedGraphsConfig() throws IOException {
        List<PredefinedGraphEntry> predefinedGraphs = new ArrayList<>();
        String resourcePath = "graph"; // 对应 resources/graph 目录
        String fileExtension = ".json";

        List<InputStream> jsonInputStreams = ResourceUtils.getResourceInputStreams(resourcePath, fileExtension);

        for (InputStream inputStream : jsonInputStreams) {
            try (InputStream is = inputStream) { // Ensure InputStream is closed
                PredefinedGraphEntry entry = OBJECT_MAPPER.readValue(is, PredefinedGraphEntry.class);
                // 假设每个 PredefinedGraphEntry 内部的 GraphDefinition 应该有一个 graphId。
                // 如果 JSON 文件本身没有顶层 uuid，这里可以生成一个。
                if (entry.getGraph() != null) {
                    if (entry.getGraph().getGraphId() == null) {
                        entry.getGraph().setGraphId(IdGenerator.generateShortId());
                    }
                }
                predefinedGraphs.add(entry);
            } catch (IOException e) {
                log.error("Error reading or parsing JSON stream from resource '{}/{}': {}", resourcePath, fileExtension,
                        e.getMessage());
            }
        }

        // 根据收集到的预定义图列表构建 GraphConfig
        return new GraphConfig().setPredefinedGraphs(predefinedGraphs);
    }
}