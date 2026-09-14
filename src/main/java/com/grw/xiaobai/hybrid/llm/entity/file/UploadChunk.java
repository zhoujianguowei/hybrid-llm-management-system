package com.grw.xiaobai.hybrid.llm.entity.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadChunk {
    private Long chunkIndex;
    private Long uploadedSize;
    private Integer progress;
    private String status;
}