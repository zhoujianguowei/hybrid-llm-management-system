package com.grw.xiaobai.hybrid.llm.service;

import cn.hutool.core.lang.Pair;
import com.grw.xiaobai.hybrid.llm.entity.system.GpuInfo;

import java.util.List;

public interface GpuService {
    List<Pair<Long, List<GpuInfo>>> getGpuHistoryInfo();

    List<GpuInfo> getGpuInfo();

    List<GpuInfo> getGpuList();

    void syncGpuInfo();
}
