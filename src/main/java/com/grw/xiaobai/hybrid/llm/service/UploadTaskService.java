package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.entity.file.UploadChunk;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadComplete;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadInit;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadTask;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface UploadTaskService {
    UploadInit initUpload(String fileName, String targetPath, Long totalSize) throws Exception;

    UploadChunk uploadChunk(String taskId, Long chunkIndex, Long chunkSize, Long offset, InputStream chunkStream) throws IOException;

    UploadComplete completeUpload(String taskId);

    List<UploadTask> getUploadList();

    Map<String, Boolean> checkFileExists(String targetPath, List<String> fileNames);

    String cancelUpload(String taskId);

    String deleteUpload(List<String> taskIds);

    List<UploadTask> getTasksByUsername(String username);

    UploadTask getTask(String taskId);

    UploadTask updateProgress(String taskId, Long uploadedSize);

    void cleanupUploadTask();

    CoreFileOperationService.ValidationResult validateUploadTask(String taskId, String username);
}
