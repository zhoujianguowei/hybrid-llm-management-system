package com.grw.xiaobai.hybrid.llm.entity.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DiskSpace {
    private Long totalSpace;
    private Long freeSpace;
    private Long usableSpace;
    private String path;
}