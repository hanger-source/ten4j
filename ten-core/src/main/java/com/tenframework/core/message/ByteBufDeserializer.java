package com.tenframework.core.message;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;

/**
 * Jackson自定义反序列化器，用于将字节数组反序列化为Netty ByteBuf
 */
public class ByteBufDeserializer extends JsonDeserializer<ByteBuf> {

    @Override
    public ByteBuf deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws IOException {
        byte[] bytes = jsonParser.getBinaryValue();
        if (bytes == null) {
            return Unpooled.EMPTY_BUFFER;
        }
        // 创建一个新的ByteBuf，引用计数为1
        return Unpooled.wrappedBuffer(bytes);
    }
}