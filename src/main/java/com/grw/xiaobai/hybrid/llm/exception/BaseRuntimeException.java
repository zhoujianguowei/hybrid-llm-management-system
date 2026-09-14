package com.grw.xiaobai.hybrid.llm.exception;

/**
 * 基础运行时异常类，除了系统异常，其它异常都归结为该异常类子类
 */

public abstract class BaseRuntimeException extends RuntimeException {
    public BaseRuntimeException() {
        this(BaseRuntimeException.class.getSimpleName());
    }

    public BaseRuntimeException(String message) {
        super(message);
    }

    /**
     * 异常详情
     *
     * @return
     */
    public abstract String errorDetail();

    public abstract Integer getCode();

    public abstract String getMsg();

    public abstract String getMsgKey();
}
