package com.grw.xiaobai.hybrid.llm.entity.system;

import lombok.Getter;
import lombok.Setter;

/**
 * GPU信息实体类
 */
@Getter
@Setter
public class GpuInfo {
    private int index;
    private String name;
    private int memoryTotal;  // 单位: MiB
    private int memoryUsed;   // 单位: MiB
    private int memoryAvailable;
    private static final int SYSTEM_KEEP_MEMORY = 512;

    public GpuInfo(int index, String name, int memoryTotal, int memoryUsed) {
        this.index = index;
        this.name = name;
        this.memoryTotal = memoryTotal;
        this.memoryUsed = memoryUsed;
        this.memoryAvailable = Math.max(0, memoryTotal - memoryUsed - SYSTEM_KEEP_MEMORY);
    }

    @Override
    public String toString() {
        return String.format("GPU #%d: %s | Memory: %d/%d MiB",
                index, name, memoryUsed, memoryTotal);
    }
}