package com.grw.xiaobai.hybrid.llm.utils.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageProcessor {

    // 声明一个静态的、最终的Logger实例
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageProcessor.class);


    public static String resizeCompressAndEncode(String imagePath, int maxDimension, float quality) throws IOException {
        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            throw new IOException("文件未找到: " + imagePath);
        }
        BufferedImage originalImage = ImageIO.read(imageFile);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Thumbnails.of(originalImage)
                .size(maxDimension, maxDimension)
                .outputQuality(quality)
                .outputFormat("jpeg")
                .toOutputStream(outputStream);

        byte[] imageBytes = outputStream.toByteArray();
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        return "data:image/jpeg;base64," + base64Image;
    }

    /**
     * 根据本地图片文件路径创建一个可用于API调用的Data URI。
     * 如果图片大小超过maxSizeBytes，会按比例进行压缩直到满足要求。
     *
     * @param imagePath    本地图片的完整路径。
     * @param maxSizeBytes 允许的最大文件大小（以字节为单位）。
     * @return 符合规范的 Data URI 字符串 (例如, "data:image/jpeg;base64,...")。
     * @throws IOException 如果文件读取或图片处理失败。
     */
    public static String createImageDataUrl(String imagePath, long maxSizeBytes, int maxDimension) throws IOException {
        File imageFile = new File(imagePath);

        if (!imageFile.exists() || !imageFile.isFile()) {
            LOGGER.error("文件未找到或不是一个有效的文件: {}", imagePath);
            throw new IOException("文件未找到或不是一个有效的文件: " + imagePath);
        }

        String formatName = getFileFormat(imageFile);
        if (formatName == null) {
            LOGGER.error("无法识别的图片格式: {}", imagePath);
            throw new IOException("无法识别的图片格式: " + imagePath);
        }

        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
        long currentSize = imageBytes.length;

        if (currentSize > maxSizeBytes) {

            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                LOGGER.error("无法读取图片内容，格式可能不受支持: {}", imagePath);
                throw new IOException("无法读取图片内容，格式可能不受支持: " + imagePath);
            }

            double scale = 0.8;

            while (currentSize > maxSizeBytes || Math.max(image.getWidth(), image.getHeight()) >= maxDimension) {
                int newWidth = (int) (image.getWidth() * scale);
                int newHeight = (int) (image.getHeight() * scale);

                if (newWidth == 0 || newHeight == 0) {
                    LOGGER.error("图片已缩到最小，但仍超过大小限制。");
                    throw new IOException("图片已缩到最小，但仍超过大小限制。");
                }

                BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, image.getType());
                Graphics2D g2d = resizedImage.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(image, 0, 0, newWidth, newHeight, null);
                g2d.dispose();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(resizedImage, formatName, baos);
                imageBytes = baos.toByteArray();
                currentSize = imageBytes.length;

                image = resizedImage;
            }
        }
        String base64String = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:image/" + formatName + ";base64," + base64String;
        return dataUrl;
    }

    private static String getImageFormatFromUrl(String imageUrl) {
        String lowerUrl = imageUrl.toLowerCase();
        if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg")) return "jpeg";
        if (lowerUrl.contains(".png")) return "png";
        if (lowerUrl.contains(".gif")) return "gif";
        if (lowerUrl.contains(".webp")) return "webp";
        return null;
    }

    private static String getFileFormat(File file) {
        String fileName = file.getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            String extension = fileName.substring(lastDotIndex + 1).toLowerCase();
            return "jpg".equals(extension) ? "jpeg" : extension;
        }
        return null;
    }

}