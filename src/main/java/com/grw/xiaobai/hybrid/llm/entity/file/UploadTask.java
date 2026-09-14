package com.grw.xiaobai.hybrid.llm.entity.file;

import com.grw.xiaobai.hybrid.llm.constant.FileTaskConstants;
import java.util.Objects;
import lombok.Data;

@Data
public class UploadTask {
    private String taskId;
    private String username;
    private String fileName;
    private String targetPath;
    private Long totalSize;
    private Long uploadedSize;
    private String status;
    private Long createdTime;
    private Long updatedTime;
    private Long completedTime;
    private String tempFilePath;
    private String errorMessage;

    public static UploadTask create(String taskId, String username, String fileName, String targetPath, Long totalSize, String tempFilePath) {
        UploadTask task = new UploadTask();
        task.setTaskId(taskId);
        task.setUsername(username);
        task.setFileName(fileName);
        task.setTargetPath(targetPath);
        task.setTotalSize(totalSize);
        task.setUploadedSize(0L);
        task.setStatus(FileTaskConstants.STATUS_PENDING);
        task.setCreatedTime(System.currentTimeMillis());
        task.setUpdatedTime(System.currentTimeMillis());
        task.setTempFilePath(tempFilePath);
        return task;
    }

    public int getProgress() {
        if (totalSize == null || totalSize == 0) return 0;
        long uploaded = uploadedSize == null ? 0L : uploadedSize;
        return (int) ((uploaded * 100) / totalSize);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        UploadTask that = (UploadTask) o;
        return Objects.equals(taskId, that.taskId) && Objects.equals(username, that.username) && Objects.equals(fileName, that.fileName) && Objects.equals(targetPath, that.targetPath) && Objects.equals(totalSize, that.totalSize) && Objects.equals(uploadedSize, that.uploadedSize) && Objects.equals(status, that.status) && Objects.equals(createdTime, that.createdTime) && Objects.equals(updatedTime, that.updatedTime) && Objects.equals(completedTime, that.completedTime) && Objects.equals(tempFilePath, that.tempFilePath) && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, username, fileName, targetPath, totalSize, uploadedSize, status, createdTime, updatedTime, completedTime, tempFilePath, errorMessage);
    }
}
