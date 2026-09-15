package com.grw.xiaobai.hybrid.llm.utils;

public class ExceptionUtil {
    public static Throwable traceRealException(Throwable e) {
        if (e.getCause() == null) {
            return e;
        }
        return traceRealException(e.getCause());
    }
}
