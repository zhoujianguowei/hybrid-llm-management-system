package com.grw.xiaobai.hybrid.llm.config;

import cn.hutool.core.thread.ThreadUtil;
import com.grw.xiaobai.hybrid.llm.constant.ThreadPoolConstants;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;


import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * 线程池配置，自定义一些常用的线程池
 */
@Configuration
public class ThreadPoolConfiguration {

    /**
     * 线程池任务队列容量
     */
    private static final int DEFAULT_TASK_QUEUE_CAPACITY = 10000;


    /**
     * 通用异步任务执行线程池，线程池拒绝策略采用的是 {@link ThreadPoolExecutor.CallerRunsPolicy}，
     * 如果任务队列过长会导致任务阻塞主线程
     *
     * @return
     */
    @Bean(name = ThreadPoolConstants.COMMON_ASYNC_TASK_EXECUTOR_NAME)
    public ExecutorService asyncTaskExecutor() {
        return new ThreadPoolExecutor(
                20, 50, 60, SECONDS,
                new LinkedBlockingQueue<>(DEFAULT_TASK_QUEUE_CAPACITY),
                ThreadUtil.createThreadFactory("common-async-thread-pool-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }



    @Bean(name = ThreadPoolConstants.CACHE_GPU_INFO_HISTORY_SCHEDULED_THREAD_POOL_EXECUTOR)
    public ScheduledExecutorService cachedGpuInfoScheduledExecutorService() {
        return Executors.newScheduledThreadPool(2);
    }


    @Bean(name = ThreadPoolConstants.GENERATE_DOWNLOAD_LINK_THREAD_POOL_EXECUTOR)
    public ExecutorService generateDownloadLinkThreadPoolExecutor() {
        return new ThreadPoolExecutor(
                10, 10, 160, SECONDS,
                new LinkedBlockingQueue<>(DEFAULT_TASK_QUEUE_CAPACITY),
                ThreadUtil.createThreadFactory("generate-download-link-thread-pool-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean(name = ThreadPoolConstants.CHAT_TASK_EXECUTOR_NAME)
    public ExecutorService chatTaskExecutor() {
        return new ThreadPoolExecutor(
                5, 20, 60, SECONDS,
                new LinkedBlockingQueue<>(DEFAULT_TASK_QUEUE_CAPACITY),
                ThreadUtil.createThreadFactory("chat-task-thread-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }


    @Bean(name = ThreadPoolConstants.TASK_SCHEDULE_NAME)
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("scheduled-task-");
        return scheduler;
    }

    @Bean(name = ThreadPoolConstants.CONVERT_URL_BASE64_NAME)
    public ExecutorService convertUrlBase64Executor() {
        return new ThreadPoolExecutor(
                24, 24, 10, MINUTES,
                new LinkedBlockingQueue<>(DEFAULT_TASK_QUEUE_CAPACITY),
                ThreadUtil.createThreadFactory("convert-url-base64-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }


    @Bean(name = ThreadPoolConstants.REBOOT_SCHEDULED_THREAD_POOL_NAME)
    public ScheduledExecutorService generateRebootScheduledExecutorService() {
        return new ScheduledThreadPoolExecutor(2, ThreadUtil.createThreadFactory("reboot-scheuled-executor-"));
    }



    @Bean(name = ThreadPoolConstants.HEARTBEAT_SCHEDULED_THREAD_POOL_NAME)
    public ScheduledExecutorService hearBeatScheduledExecutorService() {
        return Executors.newScheduledThreadPool(5);
    }


    @Bean(name = ThreadPoolConstants.SESSION_CLEAN_SCHEDULED_THREAD_POOL)
    public ScheduledExecutorService sessionCleanScheduledExecutorService() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-cleaner");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean(name = ThreadPoolConstants.UPLOAD_TASK_FLUSH_SCHEDULED_THREAD_POOL)
    public ScheduledExecutorService uploadTaskFlushScheduledExecutorService() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "upload-flush");
            t.setDaemon(true);
            return t;
        });
    }
}