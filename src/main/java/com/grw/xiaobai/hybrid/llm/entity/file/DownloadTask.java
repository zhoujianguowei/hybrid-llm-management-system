package com.grw.xiaobai.hybrid.llm.entity.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DownloadTask {
    private String taskId;
    private String username;
    private String sourcePath;
    private String outputPath;
    private String fileName;
    private Long fileSize;
    private String status;
    private Long createdTime;
    private Long completedTime;
}
