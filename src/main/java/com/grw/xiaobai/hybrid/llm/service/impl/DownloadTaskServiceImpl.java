package com.grw.xiaobai.hybrid.llm.service.impl;

import cn.hutool.core.io.FileUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.constant.FileTaskConstants;
import com.grw.xiaobai.hybrid.llm.constant.ThreadPoolConstants;
import com.grw.xiaobai.hybrid.llm.entity.file.DownloadTask;
import com.grw.xiaobai.hybrid.llm.service.DownloadTaskService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DownloadTaskServiceImpl implements DownloadTaskService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadTaskServiceImpl.class);
    private static final String ZIP_EXTENSION = ".zip";

    private static final long COMPLETED_ACCESS_IDLE_MINUTES = 30;
    private static final long COMPLETED_HARD_TTL_HOURS = 6;

    private final Map<String, DownloadTask> activeTasks = new ConcurrentHashMap<>();

    private final Cache<String, DownloadTask> taskCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(COMPLETED_ACCESS_IDLE_MINUTES, TimeUnit.MINUTES)
            .expireAfterWrite(COMPLETED_HARD_TTL_HOURS, TimeUnit.HOURS)
            .removalListener((RemovalNotification<String, DownloadTask> notification) -> {
                DownloadTask task = notification.getValue();
                if (task != null && task.getOutputPath() != null) {
                    FileUtil.del(new File(task.getOutputPath()));
                    activeTasks.remove(task.getTaskId());
                    LOGGER.info("Download zip deleted on cache eviction: taskId={}, cause={}, path={}",
                            task.getTaskId(), notification.getCause(), task.getOutputPath());
                }
            })
            .build();

    @Resource(name = ThreadPoolConstants.GENERATE_DOWNLOAD_LINK_THREAD_POOL_EXECUTOR)
    private ExecutorService generateDownloadLinkThreadPoolExecutor;

    public String createDownloadTask(String username, String sourcePath) {
        File source = new File(sourcePath);
        if (!source.exists()) {
            throw new RuntimeException("Source not found: " + sourcePath);
        }
        if (!source.isDirectory()) {
            throw new RuntimeException("Only directories can be zipped: " + sourcePath);
        }
        String taskId = UUID.randomUUID().toString();
        DownloadTask task = new DownloadTask();
        task.setTaskId(taskId);
        task.setUsername(username);
        task.setSourcePath(source.getAbsolutePath());
        task.setFileName(source.getName());
        task.setStatus(FileTaskConstants.STATUS_PENDING);
        task.setCreatedTime(System.currentTimeMillis());
        activeTasks.put(taskId, task);
        executeTask(taskId);
        return taskId;
    }

    private synchronized void moveToCache(String taskId, DownloadTask task) {
        activeTasks.remove(taskId);
        taskCache.put(taskId, task);
    }

    public List<DownloadTask> getTasksByUsername(String username) {
        List<DownloadTask> result = new ArrayList<>();
        for (DownloadTask task : activeTasks.values()) {
            if (username.equals(task.getUsername())) {
                result.add(task);
            }
        }
        for (DownloadTask task : taskCache.asMap().values()) {
            if (username.equals(task.getUsername())) {
                result.add(task);
            }
        }
        return result;
    }

    public String getOutputPath(String taskId) {
        DownloadTask task = taskCache.getIfPresent(taskId);
        if (task != null && FileTaskConstants.STATUS_COMPLETED.equals(task.getStatus())) {
            return task.getOutputPath();
        }
        task = activeTasks.get(taskId);
        if (task != null && FileTaskConstants.STATUS_COMPLETED.equals(task.getStatus())) {
            return task.getOutputPath();
        }
        return null;
    }

    public String getSourcePath(String taskId) {
        DownloadTask task = taskCache.getIfPresent(taskId);
        if (task != null) {
            return task.getSourcePath();
        }
        task = activeTasks.get(taskId);
        if (task != null) {
            return task.getSourcePath();
        }
        return null;
    }

    private void executeTask(String taskId) {
        generateDownloadLinkThreadPoolExecutor.submit(() -> {
            DownloadTask task = activeTasks.get(taskId);
            if (task == null) {
                return;
            }
            try {
                File source = new File(task.getSourcePath());
                task.setStatus(FileTaskConstants.STATUS_PROCESSING);
                String zipPath = createZip(taskId, source);
                File zipFile = new File(zipPath);
                task.setOutputPath(zipPath);
                task.setFileSize(zipFile.length());
                task.setStatus(FileTaskConstants.STATUS_COMPLETED);
                task.setCompletedTime(System.currentTimeMillis());
                moveToCache(taskId, task);
                LOGGER.info("direction task completed: taskId={}, zipPath={}", taskId, zipFile.getAbsolutePath());
            } catch (Exception e) {
                LOGGER.error("Failed to create zip for task {}", taskId, e);
                task.setStatus(FileTaskConstants.STATUS_FAILED);
                task.setCompletedTime(System.currentTimeMillis());
                moveToCache(taskId, task);
            }
        });
    }

    private String createZip(String taskId, File sourceDir) throws IOException {
        Files.createDirectories(Paths.get(FilePathConstants.DOWNLOAD_TEMP_DIR));
        String zipFileName = taskId + "_" + sourceDir.getName() + ZIP_EXTENSION;
        String zipPath = FilePathConstants.DOWNLOAD_TEMP_DIR + File.separator + zipFileName;
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath), StandardCharsets.UTF_8)) {
            addToZip(sourceDir, sourceDir.getName(), zos);
        }
        return zipPath;
    }

    private void addToZip(File file, String entryName, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null || children.length == 0) {
                zos.putNextEntry(new ZipEntry(entryName + "/"));
                zos.closeEntry();
            } else {
                for (File child : children) {
                    addToZip(child, entryName + "/" + child.getName(), zos);
                }
            }
        } else {
            zos.putNextEntry(new ZipEntry(entryName));
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[32768];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
        }
    }

    public void cleanupStaleActiveTasks() {
        File tempDir = new File(FilePathConstants.DOWNLOAD_TEMP_DIR);
        if (!tempDir.exists() || !tempDir.isDirectory()) {
            return;
        }
        File[] files = tempDir.listFiles();
        if (files == null) {
            return;
        }
        List<DownloadTask> allDownloadTaskList = Lists.newArrayList(activeTasks.values());
        allDownloadTaskList.addAll(taskCache.asMap().values());
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            if (allDownloadTaskList.stream().noneMatch(var -> file.getAbsolutePath().equals(var.getOutputPath()))) {
                LOGGER.info("Deleting orphan download zip: {}", file.getName());
                FileUtil.del(file);
            }
        }
    }
}
