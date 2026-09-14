package com.grw.xiaobai.hybrid.llm.utils.trace;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.Maps;
import lombok.Getter;

/**
 * HTTP请求参数快照，用于链路追踪日志记录
 */
public class HttpRequestSnapshot {

    private final String requestUri;
    @Getter
    private final Map<String, String[]> parameterMap;
    private final Map<String, String> headerMap;

    public HttpRequestSnapshot(HttpServletRequest request) {
        this.requestUri = request.getRequestURI();
        this.parameterMap = copyParameterMap(request.getParameterMap());
        this.headerMap = copyHeaderMap(request);
    }

    public String getRequestURI() {
        return requestUri;
    }

    public String getHeader(String name) {
        return headerMap.get(name);
    }

    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headerMap.keySet());
    }

    private static Map<String, String[]> copyParameterMap(Map<String, String[]> source) {
        Map<String, String[]> copy = Maps.newLinkedHashMap();
        if (source == null) {
            return copy;
        }
        source.forEach((name, values) ->
                copy.put(name, values == null ? null : Arrays.copyOf(values, values.length)));
        return copy;
    }

    private static Map<String, String> copyHeaderMap(HttpServletRequest request) {
        Map<String, String> headers = Maps.newLinkedHashMap();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }
}
