package source.hanger.core.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import javax.imageio.ImageIO;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil; // 导入 ByteBufUtil
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImageUtils {

    public static String convertVideoFrameToJpegBase64(ByteBuf imageData, int width, int height, int pixelFormat) {
        if (imageData == null || !imageData.isReadable()) {
            log.warn("[ImageUtils] Input imageData is null or not readable.");
            return null;
        }

        // 获取 ByteBuf 的可读字节作为 byte[]，用于后续处理
        byte[] imageBytes = ByteBufUtil.getBytes(imageData); // 这会复制数据，但对于图像处理是常见的，且不会改变原始 ByteBuf 的 readerIndex
        int actualLen = imageBytes.length;

        try {
            BufferedImage image;
            int expectedRgb = width * height * 3;
            int expectedRgba = width * height * 4;

            log.debug(
                "[ImageUtils] Detecting image format: actualLen={}, width={}, height={}, expectedRGB={}, "
                    + "expectedRGBA={}, pixelFormat={}",
                actualLen, width, height, expectedRgb, expectedRgba, pixelFormat);

            // 前端发送的JPEG数据，pixelFormat=1 表示JPEG
            // 临时处理：如果 pixelFormat 为 0 且数据长度远小于预期原始数据长度，也尝试按 JPEG 解码
            if (pixelFormat == 1) {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) { // 使用 imageBytes
                    image = ImageIO.read(bais);
                } catch (IOException e) {
                    log.error("[ImageUtils] Error decoding JPEG data: {}", e.getMessage());
                    return null;
                }
            } else if (actualLen == expectedRgb) { // RGB
                image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                image.getRaster().setDataElements(0, 0, width, height, imageBytes); // 使用 imageBytes
            } else if (actualLen == expectedRgba) { // RGBA
                BufferedImage rgbaImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                rgbaImage.getRaster().setDataElements(0, 0, width, height, imageBytes); // 使用 imageBytes
                image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); // Convert to RGB
                image.getGraphics().drawImage(rgbaImage, 0, 0, null);
            } else if (actualLen == (int)(width * height * 1.5)) { // YUV420 - requires OpenCV in Java, for simplicity, we will skip it and log an error if encountered.
                log.error("[ImageUtils] YUV420 format detected. OpenCV is required for YUV420 to RGB conversion, which is not included for simplicity.");
                return null; // For now, we don't support YUV420 without external library
            }
            else {
                log.error("[ImageUtils] Unknown image data format: len={}, width={}, height={}, pixelFormat={}",
                    actualLen, width, height, pixelFormat);
                return null;
            }

            if (image == null) {
                log.error("[ImageUtils] Image could not be decoded or created.");
                return null;
            }

            BufferedImage resizedImage = resizeImageKeepAspect(image, 512);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resizedImage, "jpeg", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            log.error("[ImageUtils] Error converting image to JPEG Base64: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 Base64 编码的图片字符串保存为 JPEG 文件。
     *
     * @param base64Image Base64 编码的图片字符串。
     * @param directory 保存图片的目录。
     * @param fileName 图片文件名，不包含扩展名。
     * @return 保存成功返回文件路径，否则返回 null。
     */
    public static String saveBase64AsJpeg(String base64Image, String directory, String fileName) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            String fullFileName = fileName + ".jpeg";
            Path filePath = dirPath.resolve(fullFileName);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(imageBytes);
            }
            log.info("[ImageUtils] 图片已保存到: {}", filePath.toAbsolutePath());
            return filePath.toAbsolutePath().toString();
        } catch (IOException e) {
            log.error("[ImageUtils] 保存图片失败: {}", e.getMessage());
            return null;
        }
    }

    private static BufferedImage resizeImageKeepAspect(BufferedImage image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        if (width <= maxSize && height <= maxSize) {
            return image;
        }

        double aspectRatio = (double) width / height;
        int newWidth;
        int newHeight;

        if (width > height) {
            newWidth = maxSize;
            newHeight = (int) (maxSize / aspectRatio);
        } else {
            newHeight = maxSize;
            newWidth = (int) (maxSize * aspectRatio);
        }

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, image.getType());
        resizedImage.getGraphics().drawImage(image, 0, 0, newWidth, newHeight, null);
        return resizedImage;
    }
}
