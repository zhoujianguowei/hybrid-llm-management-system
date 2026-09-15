package com.grw.xiaobai.hybrid.llm.task;

import com.grw.xiaobai.hybrid.llm.constant.ThreadPoolConstants;
import com.grw.xiaobai.hybrid.llm.service.GpuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class GpuInfoHistoryTask {
    private static final int INTERVAL_SECONDS = 5;
    @Resource
    private GpuService gpuService;

    @Resource(name = ThreadPoolConstants.CACHE_GPU_INFO_HISTORY_SCHEDULED_THREAD_POOL_EXECUTOR)
    private ScheduledExecutorService cacheGpuScheduledExecutor;
    private static final Logger LOGGER = LoggerFactory.getLogger(GpuInfoHistoryTask.class);

    @PostConstruct
    public void init() {
        cacheGpuScheduledExecutor.scheduleAtFixedRate(this::schedule, 3, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void schedule() {
        try {
            gpuService.syncGpuInfo();
        } catch (Exception e) {
            // 捕获异常避免 scheduleAtFixedRate 任务被永久取消
            LOGGER.error("Failed to sync GPU info history", e);
        }
    }


}
