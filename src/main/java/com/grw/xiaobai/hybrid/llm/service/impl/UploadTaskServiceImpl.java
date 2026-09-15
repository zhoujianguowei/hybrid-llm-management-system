package com.grw.xiaobai.hybrid.llm.service.impl;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.constant.ErrorCodeConstants;
import com.grw.xiaobai.hybrid.llm.service.CoreFileOperationService;
import com.grw.xiaobai.hybrid.llm.service.FilePermissionService;
import com.grw.xiaobai.hybrid.llm.service.SessionService;
import com.grw.xiaobai.hybrid.llm.service.UploadTaskService;
import com.grw.xiaobai.hybrid.llm.service.UserService;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.constant.FileTaskConstants;
import com.grw.xiaobai.hybrid.llm.constant.ThreadPoolConstants;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadChunk;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadComplete;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadInit;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadTask;
import com.grw.xiaobai.hybrid.llm.enums.FilePermissionEnum;
import com.grw.xiaobai.hybrid.llm.exception.BusinessLogicException;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.grw.xiaobai.hybrid.llm.constant.FilePathConstants.UPLOAD_USER_DIR;

@Service
public class UploadTaskServiceImpl implements UploadTaskService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadTaskServiceImpl.class);

    private final Map<String, Map<String, UploadTask>> userTasks = new ConcurrentHashMap<>();
    private final Map<String, String> taskIndex = new ConcurrentHashMap<>();
    private volatile Set<String> dirtyUsers = ConcurrentHashMap.newKeySet();
    private final Object dirtyLock = new Object();
    private final Cache<String, Object> uploadLocks = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    @Resource(name = ThreadPoolConstants.UPLOAD_TASK_FLUSH_SCHEDULED_THREAD_POOL)
    private ScheduledExecutorService flushScheduler;

    @Resource
    private UserService userService;

    @Resource
    private CoreFileOperationService coreFileOperationService;

    @Resource
    private FilePermissionService filePermissionService;

    @Resource
    private SessionService sessionService;

    @PostConstruct
    public void init() {
        try {
            FileUtil.mkdir(new File(FilePathConstants.UPLOAD_TEMP_DIR));
            FileUtil.mkdir(new File(UPLOAD_USER_DIR));
            flushScheduler.scheduleAtFixedRate(this::flushDirtyUsers, 5, 5, TimeUnit.SECONDS);
            flushScheduler.scheduleAtFixedRate(this::checkTimeoutTasks, 60, 60, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize UploadTaskService", e);
            throw new RuntimeException("failed to init upload task service");
        }
    }

    private String getUserFilePath(String username) {
        return UPLOAD_USER_DIR + File.separator + username + FileTaskConstants.RECORD_JSON;
    }

    private Map<String, UploadTask> getOrLoadUserTasks(String username) {
        return userTasks.computeIfAbsent(username, u -> {
            Map<String, UploadTask> map = new ConcurrentHashMap<>();
            File file = new File(getUserFilePath(u));
            if (file.exists()) {
                try {
                    String json = FileUtil.readString(file, StandardCharsets.UTF_8);
                    List<UploadTask> loaded = JSON.parseArray(json, UploadTask.class);
                    if (loaded != null) {
                        for (UploadTask task : loaded) {
                            map.put(task.getTaskId(), task);
                            taskIndex.put(task.getTaskId(), u);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load upload tasks for user {}", u, e);
                }
            }
            return map;
        });
    }

    private void saveUserTasks(String username) {
        Map<String, UploadTask> map = userTasks.get(username);
        if (map == null) return;
        List<UploadTask> snapshot;
        synchronized (map) {
            snapshot = new ArrayList<>(map.values());
            try {
                String json = JSON.toJSONString(snapshot, SerializerFeature.PrettyFormat);
                FileUtil.writeString(json, new File(getUserFilePath(username)), StandardCharsets.UTF_8);
            } catch (Exception e) {
                LOGGER.error("Failed to save upload tasks for user {}", username, e);
            }
        }
    }

    private void markDirty(String username) {
        if (username != null) {
            dirtyUsers.add(username);
        }
    }

    private void flushDirtyUsers() {
        Set<String> current;
        synchronized (dirtyLock) {
            if (dirtyUsers.isEmpty()) return;
            current = dirtyUsers;
            dirtyUsers = ConcurrentHashMap.newKeySet();
        }
        for (String username : current) {
            saveUserTasks(username);
        }
    }

    private Object getUploadLock(String taskId) {
        try {
            return uploadLocks.get(taskId, Object::new);
        } catch (Exception e) {
            return new Object();
        }
    }

    public String createUploadTask(String username, String fileName, String targetPath, Long totalSize) {
        Map<String, UploadTask> map = getOrLoadUserTasks(username);
        synchronized (map) {
            long activeCount = map.values().stream()
                    .filter(t -> !coreFileOperationService.isTerminated(t.getStatus()))
                    .count();
            if (activeCount >= FileTaskConstants.MAX_CONCURRENT_UPLOADS_PER_USER) {
                throw BusinessLogicException.builder()
                        .code(ErrorCodeConstants.TOO_MANY_REQUESTS)
                        .msgKey("upload.concurrent_limit")
                        .msg("Concurrent upload limit exceeded")
                        .build();
            }
        }

        String taskId = UUID.randomUUID().toString();
        String tempFilePath = FilePathConstants.UPLOAD_TEMP_DIR + File.separator + taskId + "_" + fileName;

        UploadTask task = UploadTask.create(taskId, username, fileName, targetPath, totalSize, tempFilePath);

        synchronized (map) {
            map.put(taskId, task);
            taskIndex.put(taskId, username);
            saveUserTasks(username);
        }

        return taskId;
    }

    public UploadTask getTask(String taskId) {
        String username = taskIndex.get(taskId);
        if (username == null) return null;
        Map<String, UploadTask> map = getOrLoadUserTasks(username);
        return map.get(taskId);
    }

    public UploadTask updateProgress(String taskId, Long uploadedSize) {
        String username = taskIndex.get(taskId);
        if (username == null) {
            return null;
        }
        Map<String, UploadTask> map = getOrLoadUserTasks(username);
        synchronized (map) {
            UploadTask task = map.get(taskId);
            if (task != null) {
                if (FileTaskConstants.STATUS_PENDING.equals(task.getStatus())) {
                    task.setStatus(FileTaskConstants.STATUS_PROCESSING);
                }
                task.setUploadedSize(uploadedSize);
                task.setUpdatedTime(System.currentTimeMillis());
                markDirty(username);
            }
            return task;
        }
    }


    public List<UploadTask> getTasksByUsername(String username) {
        Map<String, UploadTask> map = getOrLoadUserTasks(username);
        synchronized (map) {
            return new ArrayList<>(map.values());
        }
    }

    private void doCancelUpload(String taskId) {
        String username = taskIndex.get(taskId);
        if (username == null) return;
        Map<String, UploadTask> map = getOrLoadUserTasks(username);
        synchronized (map) {
            UploadTask task = map.get(taskId);
            if (task != null) {
                task.setStatus(FileTaskConstants.STATUS_CANCELLED);
                task.setCompletedTime(System.currentTimeMillis());
                coreFileOperationService.deleteTempFile(task.getTempFilePath());
                saveUserTasks(username);
            }
        }
    }

    public void deleteUploadTask(String taskId) {
        String username = taskIndex.get(taskId);
        if (username == null) return;
        Map<String, UploadTask> map = getOrLoadUserTasks(username);
        synchronized (map) {
            UploadTask task = map.remove(taskId);
            taskIndex.remove(taskId);
            if (task != null) {
                coreFileOperationService.deleteTempFile(task.getTempFilePath());
            }
            saveUserTasks(username);
        }
    }

    public void checkTimeoutTasks() {
        long now = System.currentTimeMillis();
        long timeoutThreshold = now - FileTaskConstants.UPLOAD_TIMEOUT_MINUTES * 60 * 1000L;
        try {
            for (Map.Entry<String, Map<String, UploadTask>> entry : userTasks.entrySet()) {
                String username = entry.getKey();
                Map<String, UploadTask> map = entry.getValue();
                boolean changed = false;
                synchronized (map) {
                    for (UploadTask task : map.values()) {
                        if (!FileTaskConstants.STATUS_PENDING.equals(task.getStatus())
                                && !FileTaskConstants.STATUS_PROCESSING.equals(task.getStatus())) {
                            continue;
                        }
                        if (task.getUpdatedTime() != null && task.getUpdatedTime() < timeoutThreshold) {
                            LOGGER.warn("Upload task timed out: taskId={}, username={}, updatedTime={}",
                                    task.getTaskId(), username, task.getUpdatedTime());
                            task.setStatus(FileTaskConstants.STATUS_FAILED);
                            task.setErrorMessage("Upload interrupted");
                            task.setCompletedTime(now);
                            changed = true;
                            coreFileOperationService.deleteTempFile(task.getTempFilePath());
                        }
                    }
                    if (changed) {
                        saveUserTasks(username);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check timeout upload tasks", e);
        }
    }

    public void cleanupUploadTask() {
        long now = System.currentTimeMillis();
        long retentionCutoff = now - FileTaskConstants.COMPLETED_RETENTION_DAYS * 24 * 60 * 60 * 1000L;
        File[] uploadTaskRecordFiles = Optional.of(new File(UPLOAD_USER_DIR)).map(File::listFiles).orElse(new File[0]);
        Function<String, String> extractUserNameFunction = var -> var.substring(0, var.length() - FileTaskConstants.RECORD_JSON.length());
        Set<String> deactiveUserNameSet = Arrays.stream(uploadTaskRecordFiles).map(File::getName).filter(var -> var.endsWith(FileTaskConstants.RECORD_JSON)
                && var.length() > FileTaskConstants.RECORD_JSON.length()).map(extractUserNameFunction).
                filter(var -> !userTasks.containsKey(var)).collect(Collectors.toSet());
        for (File file : uploadTaskRecordFiles) {
            if (!file.getName().endsWith(FileTaskConstants.RECORD_JSON) || file.getName().length() <= FileTaskConstants.RECORD_JSON.length()) {
                continue;
            }
            String username = extractUserNameFunction.apply(file.getName());
            Map<String, UploadTask> map = getOrLoadUserTasks(username);
            List<UploadTask> uploadTaskList = Lists.newArrayList(map.values());
            List<String> toRemove = new ArrayList<>();
            synchronized (map) {
                for (UploadTask task : map.values()) {
                    boolean shouldRemove = (coreFileOperationService.isTerminated(task.getStatus()) && task.getCompletedTime() != null && task.getCompletedTime() < retentionCutoff)
                            || (!coreFileOperationService.isTerminated(task.getStatus()) && !sessionService.isUserOnline(username));
                    if (shouldRemove) {
                        toRemove.add(task.getTaskId());
                        coreFileOperationService.deleteTempFile(task.getTempFilePath());
                    }
                }
                for (String taskId : toRemove) {
                    map.remove(taskId);
                    taskIndex.remove(taskId);
                }
                if (map.isEmpty()) {
                    file.delete();
                } else if (!toRemove.isEmpty()) {
                    saveUserTasks(username);
                }
                List<UploadTask> latestUploadTaskList = getTasksByUsername(username);
                if (!sessionService.isUserOnline(username) ||
                        deactiveUserNameSet.contains(username) && CollectionUtils.isEqualCollection(uploadTaskList, latestUploadTaskList)) {
                    userTasks.remove(username);
                    for (UploadTask uploadTask : uploadTaskList) {
                        taskIndex.remove(uploadTask.getTaskId());
                    }
                }
            }
        }
    }

    public UploadInit initUpload(String fileName, String targetPath, Long totalSize) throws Exception {
        if (!coreFileOperationService.checkDiskSpace(targetPath, totalSize)) {
            throw BusinessLogicException.builder()
                    .code(ErrorCodeConstants.INSUFFICIENT_STORAGE)
                    .msgKey("upload.insufficient_storage")
                    .msg("Insufficient disk space")
                    .build();
        }
        File targetDir = new File(targetPath);
        File targetFile = new File(targetDir, fileName);
        if (targetFile.exists()) {
            if (!filePermissionService.checkPermission(targetFile.getAbsolutePath(), FilePermissionEnum.DELETE.getMask())) {
                throw BusinessLogicException.builder()
                        .code(ErrorCodeConstants.FORBIDDEN)
                        .msgKey("upload.overwrite_forbidden")
                        .msg("No permission to overwrite existing file")
                        .build();
            }
        }
        String taskId = createUploadTask(userService.getCurrentUser().getUsername(), fileName, targetPath, totalSize);
        String tempFilePath = FilePathConstants.UPLOAD_TEMP_DIR + File.separator + taskId + "_" + fileName;
        File tempFile = new File(tempFilePath);
        try {
            coreFileOperationService.initTempFile(tempFile, totalSize);
        } catch (Exception e) {
            deleteUploadTask(taskId);
            throw e;
        }
        return UploadInit.builder().taskId(taskId).build();
    }

    public UploadChunk uploadChunk(String taskId, Long chunkIndex, Long chunkSize, Long offset, InputStream chunkStream) throws IOException {
        synchronized (getUploadLock(taskId)) {
            String username = userService.getCurrentUser().getUsername();
            CoreFileOperationService.ValidationResult validationResult = validateUploadTask(taskId, username);
            if (!validationResult.isValid()) {
                throw BusinessLogicException.builder()
                        .code(validationResult.getErrorCode())
                        .msgKey(validationResult.getMsgKey())
                        .msg(validationResult.getErrorMessage())
                        .build();
            }
            UploadTask task = validationResult.getTask();
            if (coreFileOperationService.isTerminated(task.getStatus())) {
                return UploadChunk.builder()
                        .chunkIndex(chunkIndex)
                        .uploadedSize(task.getUploadedSize())
                        .progress(task.getProgress())
                        .status(task.getStatus())
                        .build();
            }
            File tempFile = coreFileOperationService.getTempFile(task);
            if (tempFile == null) {
                LOGGER.error("upload tmp file has been deleted tmpFilePath={}", task.getTempFilePath());
                task.setStatus(FileTaskConstants.STATUS_FAILED);
                return UploadChunk.builder()
                        .chunkIndex(chunkIndex)
                        .uploadedSize(offset)
                        .progress(task.getProgress())
                        .status(task.getStatus())
                        .build();
            }
            coreFileOperationService.writeChunkToFile(tempFile, offset, chunkStream);
            long uploadedSize = Math.min(offset + chunkSize, task.getTotalSize());
            updateProgress(taskId, uploadedSize);
            return UploadChunk.builder()
                    .chunkIndex(chunkIndex)
                    .uploadedSize(uploadedSize)
                    .progress(task.getProgress())
                    .status(task.getStatus())
                    .build();
        }
    }

    public UploadComplete completeUpload(String taskId) {
        String username = userService.getCurrentUser().getUsername();
        CoreFileOperationService.ValidationResult validationResult = validateUploadTask(taskId, username);
        if (!validationResult.isValid()) {
            throw BusinessLogicException.builder()
                    .code(validationResult.getErrorCode())
                    .msgKey(validationResult.getMsgKey())
                    .msg(validationResult.getErrorMessage())
                    .build();
        }
        UploadTask task = validationResult.getTask();
        try {
            Map<String, UploadTask> map = getOrLoadUserTasks(username);
            synchronized (map) {
                File tempFile = coreFileOperationService.getTempFile(task);
                if (!tempFile.exists()) {
                    throw BusinessLogicException.builder()
                            .code(ErrorCodeConstants.NOT_FOUND)
                            .msgKey("upload.temp_file_not_found")
                            .msg("Temp file not found")
                            .build();
                }
                LOGGER.info("completeUpload - tempFile: {}, size: {}, targetPath: {}, fileName: {}",
                        tempFile.getAbsolutePath(), tempFile.length(), task.getTargetPath(), task.getFileName());
                File targetDir = new File(task.getTargetPath());
                if (!targetDir.exists()) {
                    FileUtil.mkdir(targetDir);
                }
                File targetFile = new File(targetDir, task.getFileName());
                try {
                    Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    LOGGER.error("move file exception,tempFile {} targetFile {}", tempFile.getAbsoluteFile(),
                            targetFile.getAbsolutePath(), e);
                    throw new RuntimeException("move file error");
                }
                LOGGER.info("completeUpload - targetFile: {}, size: {}",
                        targetFile.getAbsolutePath(), targetFile.length());
                task.setStatus(FileTaskConstants.STATUS_COMPLETED);
                return UploadComplete.builder()
                        .filePath(targetFile.getAbsolutePath())
                        .fileName(task.getFileName())
                        .build();
            }
        } catch (Exception e) {
            if (task != null) {
                task.setStatus(FileTaskConstants.STATUS_FAILED);
                task.setErrorMessage(e.getMessage());
            }
            throw e;
        } finally {
            if (task != null) {
                task.setCompletedTime(System.currentTimeMillis());
                saveUserTasks(username);
            }
        }
    }

    public List<UploadTask> getUploadList() {
        return new ArrayList<>(getTasksByUsername(userService.getCurrentUser().getUsername()));
    }

    public Map<String, Boolean> checkFileExists(String targetPath, List<String> fileNames) {
        Map<String, Boolean> result = new HashMap<>();
        for (String name : fileNames) {
            if (name != null && !name.isEmpty()) {
                result.put(name, coreFileOperationService.checkFileExists(targetPath, name));
            }
        }
        return result;
    }

    public String cancelUpload(String taskId) {
        String username = userService.getCurrentUser().getUsername();
        CoreFileOperationService.ValidationResult validationResult = validateUploadTask(taskId, username);
        if (!validationResult.isValid()) {
            throw BusinessLogicException.builder()
                    .code(validationResult.getErrorCode())
                    .msgKey(validationResult.getMsgKey())
                    .msg(validationResult.getErrorMessage())
                    .build();
        }
        doCancelUpload(taskId);
        return "Upload cancelled";
    }

    public String deleteUpload(List<String> taskIds) {
        String username = userService.getCurrentUser().getUsername();
        for (String taskId : taskIds) {
            UploadTask task = getTask(taskId);
            if (task != null && username.equals(task.getUsername())) {
                deleteUploadTask(taskId);
            }
        }
        return "Deleted";
    }

    public CoreFileOperationService.ValidationResult validateUploadTask(String taskId, String username) {
        UploadTask task = getTask(taskId);
        return coreFileOperationService.validateUploadTask(task, username);
    }

}
