package source.hanger.server.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.util.ResourceUtils;
import source.hanger.server.pojo.resource.ModelOption;
import source.hanger.server.pojo.resource.VoiceOption;
import source.hanger.server.pojo.resource.VoiceResource;

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
            loadVoiceResources("voice", voicesMap); // 调用新的专门加载 voice 资源的方法
            loadModelResources("model", modelsMap); // 调用新的专门加载 model 资源的方法
        } catch (IOException e) {
            log.error("Failed to load resources: {}", e.getMessage(), e);
        }
    }

    // 提供获取单例实例的静态方法
    public static ResourceLoader getInstance() {
        return INSTANCE;
    }

    // 专门加载 Voice 资源的方法
    private void loadVoiceResources(String resourceDir, Map<String, List<VoiceOption>> targetMap) throws IOException {
        Map<String, InputStream> streamsMap = ResourceUtils.getResourceInputStreamsMap(resourceDir, ".json");
        for (Map.Entry<String, InputStream> entry : streamsMap.entrySet()) {
            String filename = entry.getKey();
            try (InputStream is = entry.getValue()) {
                VoiceResource voiceResource = OBJECT_MAPPER.readValue(is, VoiceResource.class);
                List<VoiceOption> candidates = voiceResource.getCandidates();
                if (candidates != null) {
                    for (VoiceOption voiceOption : candidates) {
                        voiceOption.setVoiceModel(voiceResource.getVoiceModel());
                        voiceOption.setVoiceModelName(voiceResource.getVoiceModelName());
                    }
                }
                targetMap.put(filename, candidates != null ? candidates : Collections.emptyList());
                log.info("Loaded {} from {}/{}.json", filename, resourceDir, filename);
            } catch (IOException e) {
                log.error("Error reading or parsing JSON stream from resource directory '{}' with file '{}': {}",
                        resourceDir, filename, e.getMessage());
            }
        }
    }

    // 专门加载 Model 资源的方法 (从原来的 loadResources 提取并重命名)
    private void loadModelResources(String resourceDir, Map<String, List<ModelOption>> targetMap) throws IOException {
        Map<String, InputStream> streamsMap = ResourceUtils.getResourceInputStreamsMap(resourceDir, ".json");
        for (Map.Entry<String, InputStream> entry : streamsMap.entrySet()) {
            String filename = entry.getKey();
            try (InputStream is = entry.getValue()) {
                List<ModelOption> jsonArray = OBJECT_MAPPER.readValue(is, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, ModelOption.class));
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
