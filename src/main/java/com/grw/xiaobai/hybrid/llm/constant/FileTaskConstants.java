package com.grw.xiaobai.hybrid.llm.constant;

public final class FileTaskConstants {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String RECORD_JSON = "_record.json";

    /** 单个用户最大并发上传任务数。 */
    public static final int MAX_CONCURRENT_UPLOADS_PER_USER = 3;

    public static final long COMPLETED_RETENTION_DAYS = 7L;

    /** 上传任务超时时间（分钟）。 */
    public static final long UPLOAD_TIMEOUT_MINUTES = 3L;

    private FileTaskConstants() {}
}
