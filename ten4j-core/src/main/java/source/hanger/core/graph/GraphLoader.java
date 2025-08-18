package source.hanger.core.graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
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
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> resolvedMap = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> resolvedMap.put(k, resolveGraphProperties(v, externalProperties)));
            return resolvedMap;
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            List<Object> resolvedList = new java.util.ArrayList<>();
            list.forEach(item -> resolvedList.add(resolveGraphProperties(item, externalProperties)));
            return resolvedList;
        } else if (value instanceof String) {
            String str = (String) value;
            // First, try to match {{key}} from externalProperties
            java.util.regex.Matcher cmdPropMatcher = java.util.regex.Pattern.compile(
                    "\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}\\}").matcher(str);
            if (cmdPropMatcher.matches()) {
                String key = cmdPropMatcher.group(1);
                if (externalProperties != null && externalProperties.containsKey(key)) {
                    Object resolvedValue = externalProperties.get(key);
                    log.debug("Resolved command property: {} -> {}", key, resolvedValue != null ? "***" : "null");
                    return resolvedValue; // Return the actual object, not just string
                } else {
                    log.warn("Command property '{}' not found in StartGraphCommand properties. Returning empty string.",
                            key);
                    return ""; // User requested empty string if not found
                }
            }

            // If not a {{key}} placeholder, try to match {{env:VAR_NAME}} from
            java.util.regex.Matcher envMatcher = java.util.regex.Pattern.compile(
                "\\{\\{\\s*env:([a-zA-Z0-9_.]*)\\s*\\}\\}").matcher(str);
            if (envMatcher.matches()) {
                String varName = envMatcher.group(1);
                String envValue = System.getProperty(varName);
                if (envValue != null) {
                    log.debug("Resolved environment variable: {} -> ***", varName);
                    return envValue;
                } else {
                    log.warn("Environment variable '{}' not found. Returning original string for now: {}", varName,
                            str);
                    return str; // Return original string if env var not found
                }
            }
        }
        return value;
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
                        entry.getGraph().setGraphId(UUID.randomUUID().toString());
                    }
                    // 解析环境变量
                    // 假设 GraphDefinition 的属性都在 'nodes' 字段的 'property' 中
                    // !!! REMOVED: resolve env variables at graph loading time (no command
                    // properties here)
                    // if (entry.getGraph().getNodes() != null) {
                    // entry.getGraph().getNodes().forEach(node -> {
                    // if (node.getProperty() != null) {
                    // node.setProperty(
                    // (Map<String, Object>)resolveGraphProperties(node.getProperty(), null));
                    // }
                    // });
                    // }
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