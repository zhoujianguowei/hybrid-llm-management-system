package com.grw.xiaobai.hybrid.llm.entity.system;

import lombok.Data;

@Data
public class MemoryInfo {
    private final long memoryTotal;
    private final long memoryUsed;
    private final long memoryAvailable;

    public MemoryInfo(long memoryTotal, long memoryUsed, long memoryAvailable) {
        this.memoryTotal = memoryTotal;
        this.memoryUsed = memoryUsed;
        this.memoryAvailable = memoryAvailable;
    }
}