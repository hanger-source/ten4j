package source.hanger.core.extension.bailian.vision;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue; // Import LinkedBlockingQueue
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.system.tool.BaseLLMToolExtension;
import source.hanger.core.extension.system.tool.LLMTool;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.ImageUtils;

@Slf4j
public class VisionExtension extends BaseLLMToolExtension {

    private ExecutorService virtualThreadExecutor;
    private BlockingQueue<String> imageQueue; // Add BlockingQueue member variable

    public VisionExtension() {
        super();
    }

    @Override
    public void onInit(TenEnv env) {
        super.onInit(env);
        log.debug("{} onInit", env.getExtensionName());
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.imageQueue = new LinkedBlockingQueue<>(); // Initialize the queue
    }

    @Override
    public void onStop(TenEnv env) {
        log.debug("{} onStop", env.getExtensionName());
        if (virtualThreadExecutor != null) {
            virtualThreadExecutor.shutdown();
            try {
                if (!virtualThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    virtualThreadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                virtualThreadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        super.onStop(env);
    }

    @Override
    public void onVideoFrame(TenEnv env, VideoFrameMessage videoFrame) {
        if (virtualThreadExecutor == null || virtualThreadExecutor.isShutdown()) {
            log.warn("{} Virtual thread executor is not initialized or shut down, skipping video frame processing.", env.getExtensionName());
            return;
        }

        if (imageQueue == null) {
            log.error("{} Image queue is not initialized, skipping video frame processing.", env.getExtensionName());
            return;
        }

        virtualThreadExecutor.submit(() -> {
            byte[] imageData = videoFrame.getData();
            int width = videoFrame.getWidth();
            int height = videoFrame.getHeight();

            try {
                String base64Image = ImageUtils.convertVideoFrameToJpegBase64(imageData, width, height);

                if (base64Image != null) {
                    try {
                        imageQueue.put(base64Image); // Put image data into the queue
                        log.info("{} Put latest Base64 image into queue.", env.getExtensionName());
                    } catch (InterruptedException e) {
                        log.error("{} Interrupted while putting image to queue: {}", env.getExtensionName(), e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                } else {
                    log.error("{} Failed to convert video frame to JPEG Base64.", env.getExtensionName());
                }
            } catch (Exception e) {
                log.error("{} Error processing video frame: {}", env.getExtensionName(), e.getMessage());
            }
        });
    }

    @Override
    protected List<LLMTool> getTools(TenEnv env) {
        return Collections.singletonList(new VisionTool(this.imageQueue)); // Pass the member queue
    }
}
