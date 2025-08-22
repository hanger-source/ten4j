package source.hanger.core.util;

import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LatestNBuffer {

    private final int capacity;
    private final int maxFrameSize; // 每帧最大字节数
    private final UnsafeBuffer buffer;
    private final int[] frameOffsets; // 每个槽位在 buffer 中的偏移
    private int writeIndex = 0;

    public LatestNBuffer(int capacity, int maxFrameSize) {
        this.capacity = capacity;
        this.maxFrameSize = maxFrameSize;

        // 分配容量 = N * 每帧最大字节数
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(capacity * maxFrameSize);
        this.buffer = new UnsafeBuffer(byteBuffer);

        this.frameOffsets = new int[capacity];
        for (int i = 0; i < capacity; i++) {
            frameOffsets[i] = i * maxFrameSize;
        }
    }

    public synchronized void add(String base64Image) {
        byte[] data = base64Image.getBytes(StandardCharsets.US_ASCII);
        if (data.length > maxFrameSize) {
            throw new IllegalArgumentException("Frame too large for buffer slot");
        }
        int offset = frameOffsets[writeIndex];
        buffer.putBytes(offset, data);
        // 在最后一个字节标记长度
        buffer.putInt(offset + maxFrameSize - 4, data.length);

        writeIndex = (writeIndex + 1) % capacity;
    }

    public synchronized List<String> getLatest(int n) {
        List<String> result = new ArrayList<>();
        n = Math.min(n, capacity);
        for (int i = 0; i < n; i++) {
            int idx = (writeIndex - 1 - i + capacity) % capacity;
            int offset = frameOffsets[idx];
            int length = buffer.getInt(offset + maxFrameSize - 4);
            if (length > 0) {
                byte[] data = new byte[length];
                buffer.getBytes(offset, data);
                result.add(new String(data, StandardCharsets.US_ASCII));
            }
        }
        return result;
    }
}
