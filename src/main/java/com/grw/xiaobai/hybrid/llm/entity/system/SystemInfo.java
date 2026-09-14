package com.grw.xiaobai.hybrid.llm.entity.system;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SystemInfo {
    private String system;
    private int cpuCores;
    private long totalMemoryMb;
}
