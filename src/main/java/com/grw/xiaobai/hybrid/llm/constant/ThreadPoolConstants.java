package com.grw.xiaobai.hybrid.llm.constant;

/**
 * 线程池相关常量
 */
public class ThreadPoolConstants {
    /**
     * 通用异步任务线程池名称
     */
    public static final String COMMON_ASYNC_TASK_EXECUTOR_NAME = "commonAsyncTaskExecutorName";
    /**
     * 数据库查询的异步线程池
     */
    public static final String QUERY_DB_ASYNC_TASK_EXECUTOR_NAME = "queryDbAsyncTaskExecutorName";


    public static final String CACHE_GPU_INFO_HISTORY_SCHEDULED_THREAD_POOL_EXECUTOR = "cachedGpuInfoHistoryScheduledThreadPoolExecutor";

    public static final String GENERATE_DOWNLOAD_LINK_THREAD_POOL_EXECUTOR = "generateDownloadLinkThreadPoolExecutor";

    public static final String REBOOT_SCHEDULED_THREAD_POOL_NAME = "rebootScheduledThreadPool";

    public static final String CHAT_TASK_EXECUTOR_NAME = "chatTaskExecutorName";

    public static final String CONVERT_URL_BASE64_NAME = "convertUrlToBase64Executor";

    public static final String TASK_SCHEDULE_NAME = "taskSchedule";

    public static final String HEARTBEAT_SCHEDULED_THREAD_POOL_NAME = "heartBeatScheduledThreadPool";

    public static final String SESSION_CLEAN_SCHEDULED_THREAD_POOL = "sessionCleanScheduledThreadPool";

    public static final String UPLOAD_TASK_FLUSH_SCHEDULED_THREAD_POOL = "uploadTaskFlushScheduledThreadPool";
}
