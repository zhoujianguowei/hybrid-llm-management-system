package com.grw.xiaobai.hybrid.llm.utils.trace;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.base.Stopwatch;

import cn.hutool.core.util.ClassUtil;

/**
 * 方法调用链路追踪，支持记录 http 请求参数、url 等元素
 */
public class TraceLogUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceLogUtil.class);

    /** 单个追踪元素的最大输出长度 */
    private static final int MAX_ELEMENT_LENGTH = 10000;
    /** 响应体的最大输出长度 */
    private static final int MAX_RESPONSE_BODY_LENGTH = 10 * MAX_ELEMENT_LENGTH;

    public static <T> TraceLogResult<T> doTrace(Supplier<TraceLogResult<T>> resultSupplier, @Nullable HttpServletRequest request,
                                                Method method, Object[] args, List<TraceLogElement> traceElements) {
        return doTrace(resultSupplier, request, method, args, traceElements, clazz -> true);
    }

    /**
     * 执行目标方法并异步生成链路追踪日志，argFilter 决定哪些参数类型允许输出
     */
    public static <T> TraceLogResult<T> doTrace(Supplier<TraceLogResult<T>> resultSupplier, @Nullable HttpServletRequest request,
                                                Method method, Object[] args, List<TraceLogElement> traceElements,
                                                Predicate<Class<?>> argFilter) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        TraceLogResult<T> traceResult = resultSupplier.get();
        traceResult.setCostInMillis(stopwatch.elapsed(TimeUnit.MILLISECONDS));

        HttpRequestSnapshot httpRequestSnapshot = buildRequestSnapshot(request);
        Function<Object, Object> argFormatter = arg -> {
            if (arg == null || shouldTraceParam(arg, argFilter)) {
                return arg;
            }
            return String.format("ignore type %s", arg.getClass().getName());
        };
        Object[] copyArgs = Arrays.stream(args).map(argFormatter).toArray();

        CompletableFuture<String> traceLogFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return buildTraceLog(traceResult, httpRequestSnapshot, method, copyArgs, traceElements, argFilter);
            } catch (Exception e) {
                LOGGER.error("do trace log error", e);
                return "trace log error";
            }
        });
        traceResult.setLogResultFuture(traceLogFuture);
        return traceResult;
    }

    @Nullable
    private static HttpRequestSnapshot buildRequestSnapshot(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        try {
            return new HttpRequestSnapshot(request);
        } catch (Exception e) {
            LOGGER.error("convert request param error", e);
            return null;
        }
    }

    private static <T> String buildTraceLog(TraceLogResult<T> traceResult, @Nullable HttpRequestSnapshot request,
                                            Method method, Object[] args, List<TraceLogElement> traceElements,
                                            Predicate<Class<?>> argFilter) {
        List<TraceLogElement> elements = traceElements.stream().distinct().collect(Collectors.toList());
        boolean outputLogCost = elements.remove(TraceLogElement.OUTPUT_LOG_COST);
        Stopwatch stopwatch = Stopwatch.createStarted();

        StringBuilder logBuilder = new StringBuilder();
        for (TraceLogElement element : elements) {
            logBuilder.append(element.getIdentify()).append(":");
            appendElementValue(logBuilder, traceResult, request, method, args, element, argFilter);
            logBuilder.append("\n");
        }
        if (outputLogCost) {
            logBuilder.append(TraceLogElement.OUTPUT_LOG_COST.getIdentify()).append(":")
                    .append(stopwatch.elapsed(TimeUnit.MILLISECONDS)).append("ms").append("\n");
        }
        return logBuilder.toString();
    }

    private static <T> void appendElementValue(StringBuilder logBuilder, TraceLogResult<T> traceResult,
                                               @Nullable HttpRequestSnapshot request, Method method, Object[] args,
                                               TraceLogElement element, Predicate<Class<?>> argFilter) {
        switch (element) {
            case METHOD_COST:
                logBuilder.append(traceResult.getCostInMillis()).append("ms");
                return;
            case METHOD_EXCEPTION:
                Throwable exception = traceResult.getException();
                if (exception != null) {
                    logBuilder.append(String.format("exceptionClass:%s,detail=%s\n\t%s",
                            exception.getClass().getName(), exception.getMessage(),
                            ExceptionUtils.getStackTrace(exception)));
                }
                return;
            case METHOD_RETURN:
                if (traceResult.getException() == null && shouldTraceParam(traceResult.getData(), argFilter)) {
                    logBuilder.append(StringUtils.substring(serializeParamValue(traceResult.getData()), 0, MAX_RESPONSE_BODY_LENGTH));
                }
                return;
            case HTTP_REQUEST_URL:
                if (request != null) {
                    logBuilder.append(request.getRequestURI());
                }
                return;
            case HTTP_REQUEST_PARAMETER:
                if (request != null) {
                    Map<String, String[]> parameterMap = request.getParameterMap();
                    logBuilder.append(JSON.toJSONString(parameterMap, SerializerFeature.IgnoreErrorGetter));
                }
                return;
            case HTTP_REQUEST_HEADER:
                if (request != null) {
                    Enumeration<String> headerNames = request.getHeaderNames();
                    while (headerNames != null && headerNames.hasMoreElements()) {
                        String headerName = headerNames.nextElement();
                        logBuilder.append(headerName).append("=").append(request.getHeader(headerName)).append("\t");
                    }
                }
                return;
            case METHOD_FULL_NAME:
                logBuilder.append(String.format("%s.%s", method.getDeclaringClass().getName(), method.getName()));
                return;
            case METHOD_ARGS:
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (shouldTraceParam(arg, argFilter)) {
                        logBuilder.append(String.format("arg%d=%s\t", i + 1,
                                StringUtils.substring(serializeParamValue(arg), 0, MAX_ELEMENT_LENGTH)));
                    }
                }
                return;
            default:
        }
    }

    private static boolean shouldTraceParam(Object param, Predicate<Class<?>> argFilter) {
        if (param == null) {
            return true;
        }
        Class<?> paramClass = param.getClass();
        if (ClassUtil.isSimpleTypeOrArray(paramClass)) {
            return true;
        }
        if (paramClass.isArray()) {
            return argFilter.test(paramClass.getComponentType());
        }
        return argFilter.test(paramClass);
    }

    private static String serializeParamValue(Object param) {
        if (param == null) {
            return null;
        }
        return ClassUtil.isBasicType(param.getClass())
                ? String.valueOf(param)
                : JSON.toJSONString(param, SerializerFeature.IgnoreErrorGetter);
    }
}
