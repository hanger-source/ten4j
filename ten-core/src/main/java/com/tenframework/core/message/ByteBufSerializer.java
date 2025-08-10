package com.tenframework.core.message;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

/**
 * Jackson自定义序列化器，用于将Netty ByteBuf序列化为字节数组
 */
public class ByteBufSerializer extends JsonSerializer<ByteBuf> {

    @Override
    public void serialize(ByteBuf byteBuf, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (byteBuf == null) {
            jsonGenerator.writeNull();
            return;
        }

        // 将ByteBuf的内容读取到字节数组，然后序列化这个数组
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.getBytes(byteBuf.readerIndex(), bytes);
        jsonGenerator.writeBinary(bytes);

        // 注意：这里不释放ByteBuf，因为序列化器不应该负责释放
        // ByteBuf的生命周期由调用者管理
    }
}