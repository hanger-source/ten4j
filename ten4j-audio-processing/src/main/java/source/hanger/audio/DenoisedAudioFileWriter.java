package source.hanger.audio;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DenoisedAudioFileWriter {

    private final int sampleRate; // Hz
    private final int sampleWidthBytes; // 16-bit PCM (2 bytes = 16 bits)
    private final int channels; // Mono
    private final long tenSecondsInBytes;

    private BufferedOutputStream outputStream;
    private long currentChunkBytes = 0;
    private final AtomicInteger chunkCount = new AtomicInteger(0);
    private final String baseFileName;
    private final String outputDirectory;

    public DenoisedAudioFileWriter(String baseFileName, String outputDirectory, int sampleRate, int channels, int sampleWidthBytes) {
        this.baseFileName = baseFileName;
        this.outputDirectory = outputDirectory;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.sampleWidthBytes = sampleWidthBytes;
        this.tenSecondsInBytes = (long) sampleRate * sampleWidthBytes * channels * 10; // 10 seconds of audio
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

            if (audioFrame.hasArray() && audioFrame.arrayOffset() == 0) {
                outputStream.write(audioFrame.array(), audioFrame.position(), audioFrame.remaining());
                currentChunkBytes += audioFrame.remaining();
            } else {
                byte[] bytes = new byte[audioFrame.remaining()];
                audioFrame.get(bytes);
                outputStream.write(bytes);
                currentChunkBytes += bytes.length;
            }

            if (currentChunkBytes >= tenSecondsInBytes) {
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
        writeWavHeader(outputStream, sampleRate, channels, sampleWidthBytes);
        log.info("Created new WAV chunk file: {}", outputFile.getAbsolutePath());
    }

    private void closeCurrentChunkFile() {
        if (outputStream != null) {
            try {
                outputStream.flush();
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
        // Placeholder for ChunkSize, will be updated if file is not chunked. For now, assume fixed 10s chunks.
        writeInt(out, 36 + (int) tenSecondsInBytes); // ChunkSize
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
        writeInt(out, (int) tenSecondsInBytes); // Subchunk2Size (placeholder for now)
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
        log.info("DenoisedAudioFileWriter closed.");
    }
}
