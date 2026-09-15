package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.entity.file.DownloadTask;
import java.util.List;

public interface DownloadTaskService {
    String createDownloadTask(String username, String sourcePath);

    List<DownloadTask> getTasksByUsername(String username);

    String getOutputPath(String taskId);

    String getSourcePath(String taskId);

    void cleanupStaleActiveTasks();
}
