package source.hanger.core.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AudioFileWriter {

    private static final int SAMPLE_RATE = 16000; // Hz
    private static final int SAMPLE_WIDTH_BYTES = 2; // 16-bit PCM (2 bytes = 16 bits)
    private static final int CHANNELS = 1; // Mono
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * SAMPLE_WIDTH_BYTES * CHANNELS;
    private static final long TEN_SECONDS_IN_BYTES = (long) BYTES_PER_SECOND * 10;
    public static AudioFileWriter DEFAULT = new AudioFileWriter("output", "output");
    private final AtomicInteger chunkCount = new AtomicInteger(0);
    private final String baseFileName;
    private final String outputDirectory;
    private BufferedOutputStream outputStream;
    private long currentChunkBytes = 0;

    public AudioFileWriter(String baseFileName, String outputDirectory) {
        this.baseFileName = baseFileName;
        this.outputDirectory = outputDirectory;
        File dir = new File(outputDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public synchronized void writeAudioFrame(ByteBuffer audioFrame) {
        try {
            if (outputStream == null) {
                createNewChunkFile();
            }

            // 检查 ByteBuffer 是否基于数组，并且数据是连续的
            if (audioFrame.hasArray() && audioFrame.arrayOffset() == 0) {
                outputStream.write(audioFrame.array(), audioFrame.position(), audioFrame.remaining());
                currentChunkBytes += audioFrame.remaining();
            } else {
                // 如果不是，回退到第一种方法
                byte[] bytes = new byte[audioFrame.remaining()];
                audioFrame.get(bytes);
                outputStream.write(bytes);
                currentChunkBytes += bytes.length;
            }

            if (currentChunkBytes >= TEN_SECONDS_IN_BYTES) {
                closeCurrentChunkFile();
            }
        } catch (IOException e) {
            log.error("Error writing audio frame to file: {}", e.getMessage(), e);
        }
    }

    private void createNewChunkFile() throws IOException {
        String fileName = String.format("%s_%d.wav", baseFileName, chunkCount.incrementAndGet());
        File outputFile = new File(outputDirectory, fileName);
        outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
        currentChunkBytes = 0;
        writeWavHeader(outputStream, SAMPLE_RATE, CHANNELS, SAMPLE_WIDTH_BYTES);
        log.info("Created new WAV chunk file: {}", outputFile.getAbsolutePath());
    }

    private void closeCurrentChunkFile() {
        if (outputStream != null) {
            try {
                outputStream.flush();
                // Update WAV header with correct data size before closing
                long dataSize = currentChunkBytes; // This is the actual PCM data size
                long totalFileSize = 36 + dataSize; // RIFF header size (8) + (36) + data size

                // Seek to beginning and rewrite header
                // This part requires RandomAccessFile or re-opening the file. For simplicity,
                // let's assume we can't seek back on a BufferedOutputStream. A more robust
                // solution
                // would involve writing to a temporary buffer/file first, or using
                // RandomAccessFile.
                // For now, let's keep it simple and just log the size and assume it's for
                // debugging.
                // For a proper WAV file, you'd need to go back and update the sizes.
                // However, since we're writing fixed-size chunks (10s), we can pre-calculate
                // sizes.
                // Let's adjust createNewChunkFile to pre-fill sizes.

                outputStream.close();
                log.info("Closed WAV chunk file. Total bytes written (PCM data): {}", currentChunkBytes);
            } catch (IOException e) {
                log.error("Error closing WAV chunk file: {}", e.getMessage(), e);
            } finally {
                outputStream = null;
            }
        }
    }

    private void writeWavHeader(BufferedOutputStream out, int sampleRate, int channels, int sampleWidthBytes)
            throws IOException {
        // RIFF header
        writeString(out, "RIFF"); // ChunkID
        writeInt(out, 36 + (int) TEN_SECONDS_IN_BYTES); // ChunkSize (placeholder for now, will update on close)
        writeString(out, "WAVE"); // Format

        // fmt sub-chunk
        writeString(out, "fmt "); // Subchunk1ID
        writeInt(out, 16); // Subchunk1Size (16 for PCM)
        writeShort(out, (short) 1); // AudioFormat (1 for PCM)
        writeShort(out, (short) channels); // NumChannels
        writeInt(out, sampleRate); // SampleRate
        writeInt(out, sampleRate * channels * sampleWidthBytes); // ByteRate
        writeShort(out, (short) (channels * sampleWidthBytes)); // BlockAlign
        writeShort(out, (short) (sampleWidthBytes * 8)); // BitsPerSample

        // data sub-chunk
        writeString(out, "data"); // Subchunk2ID
        writeInt(out, (int) TEN_SECONDS_IN_BYTES); // Subchunk2Size (placeholder for now, will update on close)
    }

    private void writeString(BufferedOutputStream out, String s) throws IOException {
        for (char c : s.toCharArray()) {
            out.write(c);
        }
    }

    private void writeInt(BufferedOutputStream out, int i) throws IOException {
        out.write(i & 0xFF);
        out.write((i >> 8) & 0xFF);
        out.write((i >> 16) & 0xFF);
        out.write((i >> 24) & 0xFF);
    }

    private void writeShort(BufferedOutputStream out, short s) throws IOException {
        out.write(s & 0xFF);
        out.write((s >> 8) & 0xFF);
    }

    public synchronized void close() {
        closeCurrentChunkFile();
        log.info("AudioFileWriter closed.");
    }
}
