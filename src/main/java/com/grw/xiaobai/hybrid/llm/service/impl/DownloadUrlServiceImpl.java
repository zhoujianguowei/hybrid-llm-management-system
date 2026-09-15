package com.grw.xiaobai.hybrid.llm.service.impl;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.grw.xiaobai.hybrid.llm.service.DownloadUrlService;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DownloadUrlServiceImpl implements DownloadUrlService {
    private static final long SIX_HOURS = 6 * 60 * 60 * 1000L;
    private static final String URL_DATA_FILE = System.getProperty("java.io.tmpdir") + File.separator + "file-manager-download-urls.json";

    private final Map<String, DownloadUrlInfo> urlMap = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadUrlServiceImpl.class);

    @PostConstruct
    public void init() {
        try {
            loadUrls();
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::cleanupExpiredUrls, 1, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize DownloadUrlService", e);
        }
    }

    private void loadUrls() {
        try {
            File file = new File(URL_DATA_FILE);
            if (file.exists()) {
                List<DownloadUrlInfo> loadedUrls = JSON.parseArray(FileUtil.readString(file, StandardCharsets.UTF_8), DownloadUrlInfo.class);
                if (loadedUrls == null) {
                    return;
                }
                for (DownloadUrlInfo info : loadedUrls) {
                    if (System.currentTimeMillis() - info.getCreatedTime() < SIX_HOURS) {
                        urlMap.put(info.getUrlId(), info);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load URLs", e);
        }
    }

    private void saveUrls() {
        try {
            String json = JSON.toJSONString(new ArrayList<>(urlMap.values()), SerializerFeature.PrettyFormat);
            try (FileWriter writer = new FileWriter(new File(URL_DATA_FILE))) {
                writer.write(json);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save URLs", e);
        }
    }

    public String generateDownloadUrl(String taskId, String filePath, String sessionId) {
        String urlId = UUID.randomUUID().toString();
        DownloadUrlInfo info = new DownloadUrlInfo();
        info.setUrlId(urlId);
        info.setTaskId(taskId);
        info.setFilePath(filePath);
        info.setSessionId(sessionId);
        info.setCreatedTime(System.currentTimeMillis());

        urlMap.put(urlId, info);
        saveUrls();

        return urlId;
    }

    public DownloadUrlInfo getDownloadInfo(String urlId) {
        DownloadUrlInfo info = urlMap.get(urlId);
        if (info != null) {
            if (System.currentTimeMillis() - info.getCreatedTime() > SIX_HOURS) {
                urlMap.remove(urlId);
                saveUrls();
                return null;
            }
        }
        return info;
    }

    public boolean isValidUrl(String urlId) {
        DownloadUrlInfo info = urlMap.get(urlId);
        if (info == null) return false;
        return System.currentTimeMillis() - info.getCreatedTime() <= SIX_HOURS;
    }

    private void cleanupExpiredUrls() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (DownloadUrlInfo info : urlMap.values()) {
            if (now - info.getCreatedTime() > SIX_HOURS) {
                toRemove.add(info.getUrlId());
            }
        }

        for (String urlId : toRemove) {
            urlMap.remove(urlId);
        }

        if (!toRemove.isEmpty()) {
            saveUrls();
        }
    }

    @PreDestroy
    public void cleanup() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

}
