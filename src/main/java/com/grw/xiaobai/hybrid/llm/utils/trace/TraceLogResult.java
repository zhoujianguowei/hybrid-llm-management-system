package com.grw.xiaobai.hybrid.llm.utils.trace;

import java.util.concurrent.CompletableFuture;

import lombok.Data;

/**
 * 链路追踪执行结果
 */
@Data
public class TraceLogResult<T> {
    /** 目标方法返回值 */
    private T data;
    /** 目标方法抛出的异常 */
    private Throwable exception;
    /** 目标方法耗时（毫秒） */
    private long costInMillis;
    /** 异步生成的追踪日志 */
    private CompletableFuture<String> logResultFuture;
}
