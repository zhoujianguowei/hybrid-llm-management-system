package com.grw.xiaobai.hybrid.llm.utils.image;

import cn.hutool.core.io.FileUtil;
import com.google.common.cache.CacheBuilder;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class MediaCacheHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MediaCacheHandler.class);

    private static final String RESOURCE_DIR = FilePathConstants.RESOURCE_DIR;

    // 图片最终(压缩/缩放后)发送 LLM 的大小上限，超过则自动降采样
    private static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    private static final int MAX_DIMENSION = Integer.MAX_VALUE;

    private static final LoadingCache<String, String> MEDIA_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS)
            .maximumSize(1000)
            .build(new CacheLoader<String, String>() {
                @Override
                public String load(String url) {
                    String filename = extractFilename(url);
                    String filePath = RESOURCE_DIR + File.separator + filename;
                    File file = new File(filePath);
                    if (!file.exists()) {
                        LOGGER.error("Media file not found: {}", filePath);
                        return url;
                    }
                    try {
                        String base64 = ImageProcessor.createImageDataUrl(filePath, MAX_SIZE_BYTES, MAX_DIMENSION);
                        LOGGER.info("Converted URL to base64: {} -> {} KB", url, base64.length() / 1024.0);
                        return base64;
                    } catch (IOException e) {
                        LOGGER.error("Failed to convert URL to base64: {}", url, e);
                        return url;
                    }
                }
            });

    private static final LoadingCache<String, String> TEXT_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS)
            .maximumSize(5000)
            .build(new CacheLoader<String, String>() {
                @Override
                public String load(String url) {
                    String filename = extractFilename(url);
                    String filePath = RESOURCE_DIR + File.separator + filename;
                    File file = new File(filePath);
                    if (!file.exists()) {
                        LOGGER.error("Text file not found: {}", filePath);
                        return "";
                    }
                    String content = FileUtil.readString(file, StandardCharsets.UTF_8);
                    LOGGER.info("Converted URL to text: {} -> {} chars", url, content.length());
                    return content;
                }
            });

    private static final LoadingCache<String, byte[]> COMPRESSED_IMAGE_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS)
            .maximumSize(2000)
            .build(new CacheLoader<String, byte[]>() {
                @Override
                public byte[] load(String cacheKey) {
                    String[] parts = cacheKey.split("\\|");
                    String filename = extractFilename(parts[0]);
                    String filePath = RESOURCE_DIR + File.separator + filename;
                    File file = new File(filePath);
                    if (!file.exists()) {
                        LOGGER.error("Media file not found: {}", filePath);
                        return new byte[0];
                    }
                    float quality = Float.parseFloat(parts[1]);
                    int width = Integer.parseInt(parts[2]);
                    int height = Integer.parseInt(parts[3]);
                    try {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        Thumbnails.of(file)
                                .size(width, height)
                                .outputQuality(quality)
                                .outputFormat("jpg")
                                .toOutputStream(outputStream);
                        byte[] byteArray = outputStream.toByteArray();
                        LOGGER.info("Compressed image: {} -> {}x{} @ quality={},size={} bytes", filename, width, height, quality, byteArray.length);
                        return byteArray;
                    } catch (IOException e) {
                        LOGGER.error("Failed to compress image: {}", filePath, e);
                        return new byte[0];
                    }
                }
            });

    public static String convertUrlToText(String url) {
        if (!isHttpUrl(url)) {
            return "";
        }
        return TEXT_CACHE.getUnchecked(url);
    }

    public static String convertUrlToBase64(String url) {
        if (!isHttpUrl(url)) {
            return url;
        }
        return MEDIA_CACHE.getUnchecked(url);
    }

    public static byte[] compressImageToBytes(String filename, Integer width, Integer height, Float quality) {
        String filePath = RESOURCE_DIR + File.separator + filename;
        File file = new File(filePath);
        if (!file.exists()) {
            LOGGER.error("Media file not found: {}", filePath);
            return new byte[0];
        }
        int w = width != null ? width : 1024;
        int h = height != null ? height : 1024;
        float q = quality != null ? quality : 0.85f;
        String cacheKey = filename + "|" + q + "|" + w + "|" + h;
        try {
            return COMPRESSED_IMAGE_CACHE.get(cacheKey);
        } catch (Exception e) {
            LOGGER.error("Failed to get compressed image for: {}", filename, e);
            return new byte[0];
        }
    }

    private static boolean isHttpUrl(String url) {
        return StringUtils.hasText(url) && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static String extractFilename(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex == -1 || lastSlashIndex == url.length() - 1) {
            return url;
        }
        String pathAfterSlash = url.substring(lastSlashIndex + 1);
        int queryIndex = pathAfterSlash.indexOf('?');
        if (queryIndex != -1) {
            return pathAfterSlash.substring(0, queryIndex);
        }
        return pathAfterSlash;
    }

    public static void cleanupMediaFiles(List<String> urlList) {
        if (CollectionUtils.isEmpty(urlList)) {
            return;
        }
        int deletedCount = 0;
        for (String url : urlList) {
            if (!isHttpUrl(url)) {
                continue;
            }
            MEDIA_CACHE.invalidate(url);
            TEXT_CACHE.invalidate(url);
            String filename = extractFilename(url);
            File file = new File(RESOURCE_DIR + File.separator + filename);
            if (file.exists() && file.delete()) {
                deletedCount++;
                LOGGER.info("Deleted media file: {}", filename);
            }
        }
        if (deletedCount > 0) {
            LOGGER.info("Cleaned up {} media files", deletedCount);
        }
    }

    public static void cleanupMediaFiles(List<String> imageUrlList, List<String> videoUrlList, List<String> audioUrlList) {
        cleanupMediaFiles(imageUrlList);
        cleanupMediaFiles(videoUrlList);
        cleanupMediaFiles(audioUrlList);
    }
}
