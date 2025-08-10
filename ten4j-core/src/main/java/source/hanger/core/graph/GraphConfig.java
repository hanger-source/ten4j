package source.hanger.core.graph;

// Force recompile marker

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.Accessors;

/**
 * 表示 Ten 框架的整体配置，可以包含预定义的图、日志级别等。
 * 对应 property.json 的顶层 "ten" 字段下的内容。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class GraphConfig { // 重命名为 TenConfig 更合适，但为了保持与用户提及的 GraphConfig 一致，暂时保留

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 用于存储所有配置属性的通用 Map
    private final Map<String, Object> properties = new ConcurrentHashMap<>();

    public GraphConfig(Map<String, Object> initialProperties) {
        if (initialProperties != null) {
            this.properties.putAll(initialProperties);
        }
    }

    /**
     * 预定义的图配置列表。
     * 对应 property.json 中的 "predefined_graphs" 数组。
     */
    @JsonProperty("predefined_graphs")
    private List<PredefinedGraphEntry> predefinedGraphs;

    /**
     * 日志配置。
     * 对应 property.json 中的 "log" 字段。
     */
    @JsonProperty("log")
    private LogConfig logConfig; // 新增 LogConfig DTO 来处理日志配置

    /**
     * 获取任意配置属性。
     *
     * @param path 属性的路径 (支持点分隔的嵌套路径)。
     * @return 属性值，如果不存在则为 Optional.empty()。
     */
    public Optional<Object> getProperty(String path) {
        return getPropertyInternal(properties, path);
    }

    /**
     * 设置任意配置属性。
     *
     * @param path  属性的路径 (支持点分隔的嵌套路径)。
     * @param value 属性值。
     */
    public void setProperty(String path, Object value) {
        setPropertyInternal(properties, path, value);
    }

    /**
     * 检查是否存在某个属性。
     *
     * @param path 属性的路径。
     * @return 如果存在则为 true，否则为 false。
     */
    public boolean hasProperty(String path) {
        return getProperty(path).isPresent();
    }

    /**
     * 删除某个属性。
     *
     * @param path 属性的路径。
     */
    public void deleteProperty(String path) {
        deletePropertyInternal(properties, path);
    }

    public Optional<Integer> getPropertyInt(String path) {
        return getProperty(path).map(o -> {
            if (o instanceof Integer) {
                return (Integer) o;
            } else if (o instanceof String) {
                try {
                    return Integer.parseInt((String) o);
                } catch (NumberFormatException e) {
                    return null; // 或者抛出异常
                }
            } else {
                return null; // 或者抛出异常
            }
        });
    }

    public void setPropertyInt(String path, int value) {
        setProperty(path, value);
    }

    public Optional<Long> getPropertyLong(String path) {
        return getProperty(path).map(o -> {
            if (o instanceof Long) {
                return (Long) o;
            } else if (o instanceof Integer) {
                return ((Integer) o).longValue();
            } else if (o instanceof String) {
                try {
                    return Long.parseLong((String) o);
                } catch (NumberFormatException e) {
                    return null; // 或者抛出异常
                }
            } else {
                return null; // 或者抛出异常
            }
        });
    }

    public void setPropertyLong(String path, long value) {
        setProperty(path, value);
    }

    public Optional<String> getPropertyString(String path) {
        return getProperty(path).map(Object::toString);
    }

    public void setPropertyString(String path, String value) {
        setProperty(path, value);
    }

    public Optional<Boolean> getPropertyBool(String path) {
        return getProperty(path).map(o -> {
            if (o instanceof Boolean) {
                return (Boolean) o;
            } else if (o instanceof String) {
                return Boolean.parseBoolean((String) o);
            } else {
                return null;
            }
        });
    }

    public void setPropertyBool(String path, boolean value) {
        setProperty(path, value);
    }

    public Optional<Double> getPropertyDouble(String path) {
        return getProperty(path).map(o -> {
            if (o instanceof Double) {
                return (Double) o;
            } else if (o instanceof Float) {
                return ((Float) o).doubleValue();
            } else if (o instanceof String) {
                try {
                    return Double.parseDouble((String) o);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        });
    }

    public void setPropertyDouble(String path, double value) {
        setProperty(path, value);
    }

    public Optional<Float> getPropertyFloat(String path) {
        return getProperty(path).map(o -> {
            if (o instanceof Float) {
                return (Float) o;
            } else if (o instanceof Double) {
                return ((Double) o).floatValue();
            } else if (o instanceof String) {
                try {
                    return Float.parseFloat((String) o);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        });
    }

    public void setPropertyFloat(String path, float value) {
        setProperty(path, value);
    }

    public void initPropertyFromJson(String jsonStr) {
        try {
            Map<String, Object> jsonMap = OBJECT_MAPPER.readValue(jsonStr, Map.class);
            this.properties.putAll(jsonMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to initialize properties from JSON", e);
        }
    }

    @JsonAnyGetter
    public Map<String, Object> any() {
        return properties;
    }

    @JsonAnySetter
    public void set(String name, Object value) {
        properties.put(name, value);
    }

    // 辅助方法，用于处理嵌套路径
    private Optional<Object> getPropertyInternal(Map<String, Object> currentMap, String path) {
        String[] parts = path.split("\\.", 2);
        String currentKey = parts[0];
        if (!currentMap.containsKey(currentKey)) {
            return Optional.empty();
        }

        Object value = currentMap.get(currentKey);
        if (parts.length == 1) {
            return Optional.ofNullable(value);
        } else {
            if (value instanceof Map) {
                return getPropertyInternal((Map<String, Object>) value, parts[1]);
            } else {
                return Optional.empty(); // 路径中有嵌套，但当前值不是Map
            }
        }
    }

    private void setPropertyInternal(Map<String, Object> currentMap, String path, Object value) {
        String[] parts = path.split("\\.", 2);
        String currentKey = parts[0];
        if (parts.length == 1) {
            currentMap.put(currentKey, value);
        } else {
            currentMap.computeIfAbsent(currentKey, k -> new ConcurrentHashMap<>());
            if (currentMap.get(currentKey) instanceof Map) {
                setPropertyInternal((Map<String, Object>) currentMap.get(currentKey), parts[1], value);
            } else {
                // 如果中间节点不是Map，则抛出异常或覆盖
                throw new IllegalArgumentException(
                        "Cannot set property: intermediate path '" + currentKey + "' is not a map.");
            }
        }
    }

    private void deletePropertyInternal(Map<String, Object> currentMap, String path) {
        String[] parts = path.split("\\.", 2);
        String currentKey = parts[0];
        if (!currentMap.containsKey(currentKey)) {
            return;
        }

        if (parts.length == 1) {
            currentMap.remove(currentKey);
        } else {
            Object value = currentMap.get(currentKey);
            if (value instanceof Map) {
                deletePropertyInternal((Map<String, Object>) value, parts[1]);
            }
        }
    }

    public Map<String, Object> toMap() {
        return new HashMap<>(properties);
    }
}
