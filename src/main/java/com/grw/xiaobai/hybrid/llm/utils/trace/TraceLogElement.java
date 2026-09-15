package com.grw.xiaobai.hybrid.llm.utils.trace;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 链路追踪元素类型枚举
 */
@AllArgsConstructor
@Getter
public enum TraceLogElement {

    HTTP_REQUEST_URL("url"),
    HTTP_REQUEST_PARAMETER("http_parameter"),
    HTTP_REQUEST_HEADER("http_header"),
    METHOD_FULL_NAME("method_full_name"),
    METHOD_ARGS("method_args"),
    METHOD_RETURN("method_return"),
    METHOD_COST("method_cost"),
    METHOD_EXCEPTION("exception"),
    OUTPUT_LOG_COST("output_log_cost");

    /** 日志输出时使用的元素标识 */
    private final String identify;
}
