package com.tenframework.core.graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

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
            throw new FileNotFoundException("Graph config file not found: " + classPath);
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
}