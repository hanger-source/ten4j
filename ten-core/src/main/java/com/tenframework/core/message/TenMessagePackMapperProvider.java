package com.tenframework.core.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import static com.tenframework.core.message.MessageConstants.TEN_MSGPACK_EXT_TYPE_MSG; // Changed import path

/**
 * 提供统一配置的ObjectMapper实例，用于MsgPack序列化和反序列化。
 * 确保正确注册了TEN框架自定义的MsgPack扩展类型。
 */
public class TenMessagePackMapperProvider {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        // 创建一个MessagePackFactory实例，用于处理MsgPack格式
        MessagePackFactory factory = new MessagePackFactory();
        // 注册自定义的扩展类型。
        // 这里暂时不直接注册Message.class，因为Message是抽象类，且多态由@JsonTypeInfo处理
        // factory.register(Message.class, TEN_MSGPACK_EXT_TYPE_MSG); //
        // 示例：如果Message是具体类

        OBJECT_MAPPER = JsonMapper.builder(factory)
                // .addModule(new ParameterNamesModule()) // Removed ParameterNamesModule
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // 可选：日期不转换为时间戳
                .build();
    }

    private TenMessagePackMapperProvider() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取预配置的ObjectMapper实例。
     *
     * @return ObjectMapper实例
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}