package com.grw.xiaobai.hybrid.llm.service;

import cn.hutool.core.lang.Pair;
import com.grw.xiaobai.hybrid.llm.entity.system.MemoryInfo;

import java.util.List;

public interface MemoryService {
    List<Pair<Long, MemoryInfo>> getMemoryHistory();

    MemoryInfo getCurrentMemoryInfo();

    void syncMemoryInfo();


}
