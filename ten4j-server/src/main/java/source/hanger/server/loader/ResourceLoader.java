package source.hanger.server.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.util.ResourceUtils;
import source.hanger.server.pojo.resource.ModelOption;
import source.hanger.server.pojo.resource.VoiceOption;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ResourceLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Map<String, List<VoiceOption>> voicesMap = new HashMap<>();
    private final Map<String, List<ModelOption>> modelsMap = new HashMap<>();

    // 单例实例
    private static final ResourceLoader INSTANCE = new ResourceLoader();

    // 私有构造函数，防止外部实例化
    private ResourceLoader() {
        log.info("Loading voices and models from classpath...");
        try {
            loadResources("voice", voicesMap, VoiceOption.class);
            loadResources("model", modelsMap, ModelOption.class);
        } catch (IOException e) {
            log.error("Failed to load resources: {}", e.getMessage(), e);
        }
    }

    // 提供获取单例实例的静态方法
    public static ResourceLoader getInstance() {
        return INSTANCE;
    }

    private <T> void loadResources(String resourceDir, Map<String, List<T>> targetMap, Class<T> valueType) throws IOException {
        Map<String, InputStream> streamsMap = ResourceUtils.getResourceInputStreamsMap(resourceDir, ".json");
        for (Map.Entry<String, InputStream> entry : streamsMap.entrySet()) {
            String filename = entry.getKey();
            try (InputStream is = entry.getValue()) {
                List<T> jsonArray = OBJECT_MAPPER.readValue(is, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, valueType));
                targetMap.put(filename, jsonArray);
                log.info("Loaded {} from {}/{}.json", filename, resourceDir, filename);
            } catch (IOException e) {
                log.error("Error reading or parsing JSON stream from resource directory '{}' with file '{}': {}",
                        resourceDir, filename, e.getMessage());
            }
        }
    }

    public Map<String, List<VoiceOption>> getVoicesMap() {
        return Collections.unmodifiableMap(voicesMap);
    }

    public Map<String, List<ModelOption>> getModelsMap() {
        return Collections.unmodifiableMap(modelsMap);
    }
}
