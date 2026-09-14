package com.grw.xiaobai.hybrid.llm.task;

import cn.hutool.core.io.FileUtil;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.service.DownloadTaskService;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import com.grw.xiaobai.hybrid.llm.service.UploadTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FileCleanTask {
    private static final long CLEAN_UPLOAD_TMP_FILE_THRESHOLD_MILLIS = TimeUnit.MINUTES.toMillis(10);
    @Resource
    private DownloadTaskService downloadTaskService;
    @Resource
    private UploadTaskService uploadTaskService;

    @PostConstruct
    public void init() {
        FileUtil.mkdir(new File(FilePathConstants.DOWNLOAD_TEMP_DIR));
        FileUtil.mkdir(new File(FilePathConstants.UPLOAD_TEMP_DIR));
    }

    @Scheduled(cron = "0 0/10 * * * ?")
    public void cleanupStaleUploadTempFiles() {
        log.info("Starting cleanup of stale upload temp files...");
        try {
            File tempDir = new File(FilePathConstants.UPLOAD_TEMP_DIR);
            if (!tempDir.exists() || !tempDir.isDirectory()) {
                return;
            }
            List<File> files = Arrays.asList(Objects.requireNonNull(tempDir.listFiles()));
            if (files.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            long staleThreshold = now - CLEAN_UPLOAD_TMP_FILE_THRESHOLD_MILLIS;
            int deleted = 0;
            for (File file : files) {
                if (file.isFile() && file.lastModified() < staleThreshold) {
                    log.info("Deleting stale upload temp file: {}", file.getName());
                    FileUtil.del(file);
                    deleted++;
                }
            }
            log.info("Deleted {} stale upload temp files", deleted);
        } catch (Exception e) {
            log.error("Failed to cleanup stale upload temp files", e);
        }
    }


    @Scheduled(cron = "0 0/5 * * * ?")
    public void cleanupOldUploadRecords() {
        log.info("Starting cleanup of old upload records...");
        uploadTaskService.cleanupUploadTask();
    }


    @Scheduled(cron = "0 0/5 * * * ?")
    public void cleanupStaleActiveDownloadTasks() {
        try {
            downloadTaskService.cleanupStaleActiveTasks();
        } catch (Exception e) {
            log.error("Failed to cleanup stale active download tasks", e);
        }
    }
}
