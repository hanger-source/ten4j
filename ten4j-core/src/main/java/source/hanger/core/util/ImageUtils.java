package source.hanger.core.util;

import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

@Slf4j
public class ImageUtils {

    public static String convertVideoFrameToJpegBase64(byte[] rgbData, int width, int height) {
        try {
            BufferedImage image;
            int actualLen = rgbData.length;
            int expectedRgb = width * height * 3;
            int expectedRgba = width * height * 4;

            log.info("[ImageUtils] Detecting image format: actualLen={}, width={}, height={}, expectedRGB={}, expectedRGBA={}",
                    actualLen, width, height, expectedRgb, expectedRgba);

            if (actualLen == expectedRgb) { // RGB
                image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                image.getRaster().setDataElements(0, 0, width, height, rgbData);
            } else if (actualLen == expectedRgba) { // RGBA
                BufferedImage rgbaImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                rgbaImage.getRaster().setDataElements(0, 0, width, height, rgbData);
                image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); // Convert to RGB
                image.getGraphics().drawImage(rgbaImage, 0, 0, null);
            } else if (actualLen == (int)(width * height * 1.5)) { // YUV420 - requires OpenCV in Java, for simplicity, we will skip it and log an error if encountered.
                log.error("[ImageUtils] YUV420 format detected. OpenCV is required for YUV420 to RGB conversion, which is not included for simplicity.");
                return null; // For now, we don't support YUV420 without external library
            }
            else {
                log.error("[ImageUtils] Unknown image data format: len={}, width={}, height={}", actualLen, width, height);
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
