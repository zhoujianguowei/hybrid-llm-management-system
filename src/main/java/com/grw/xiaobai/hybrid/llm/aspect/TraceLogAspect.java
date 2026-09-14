package com.grw.xiaobai.hybrid.llm.aspect;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.grw.xiaobai.hybrid.llm.utils.trace.IgnoreLog;
import com.grw.xiaobai.hybrid.llm.utils.trace.TraceLogElement;
import com.grw.xiaobai.hybrid.llm.utils.trace.TraceLogResult;
import com.grw.xiaobai.hybrid.llm.utils.trace.TraceLogUtil;
import com.grw.xiaobai.hybrid.llm.constant.TraceConstants;
import com.grw.xiaobai.hybrid.llm.utils.ThreadLocalContext;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.commons.lang3.RandomStringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.MethodInvocationProceedingJoinPoint;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

@Aspect
@Component
@Order(1)
public class TraceLogAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(TraceLogAspect.class);
    private static final int RESULT_LOG_LENGTH_LIMIT = 20000;
    /**
     * 忽略打印参数的类
     */
    private static final Set<Class<?>> IGNORE_TRACE_LOG_CLZ_SET = Sets.newHashSet(OutputStream.class,ServletRequest.class, ServletResponse.class,
            InputStreamSource.class, byte[].class);

    @Around(value = "@within(org.springframework.web.bind.annotation.RestController)")
    public Object traceLog(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        MethodInvocationProceedingJoinPoint mjp = (MethodInvocationProceedingJoinPoint) jp;
        MethodSignature methodSignature = (MethodSignature) mjp.getSignature();
        Method method = methodSignature.getMethod();
        String traceVal = MDC.get(TraceConstants.TRACE_ID_KEY);
        boolean addTrace = false;
        if (traceVal == null) {
            traceVal = RandomStringUtils.randomAlphabetic(16);
            MDC.put(TraceConstants.TRACE_ID_KEY, traceVal);
            addTrace = true;
        }
        Class<?> clz = method.getDeclaringClass();
        if (AnnotationUtils.findAnnotation(clz, IgnoreLog.class) != null || AnnotationUtils.findAnnotation(method, IgnoreLog.class) != null) {
            return jp.proceed();
        }
        TraceLogResult<Object> traceResult;
        Supplier<TraceLogResult<Object>> supplier = () -> {
            TraceLogResult<Object> result = new TraceLogResult<>();
            try {
                result.setData(jp.proceed());
            } catch (Throwable throwable) {
                result.setException(throwable);
            }
            return result;
        };
        //忽略追溯的请求入参
        Predicate<Class<?>> argFilter = var -> var == null || IGNORE_TRACE_LOG_CLZ_SET.stream().noneMatch(ignoreClz -> ignoreClz.isAssignableFrom(var));
        if (AnnotationUtils.findAnnotation(clz, RestController.class) != null) {
            traceResult = TraceLogUtil.doTrace(supplier, ThreadLocalContext.get(TraceConstants.HTTP_REQUEST_KEY),
                    method, args, Lists.newArrayList(TraceLogElement.HTTP_REQUEST_URL
                            , TraceLogElement.HTTP_REQUEST_HEADER, TraceLogElement.HTTP_REQUEST_PARAMETER,
                            TraceLogElement.METHOD_FULL_NAME,
                            TraceLogElement.METHOD_ARGS, TraceLogElement.METHOD_RETURN,
                            TraceLogElement.METHOD_EXCEPTION,
                            TraceLogElement.METHOD_COST, TraceLogElement.OUTPUT_LOG_COST), argFilter);
        } else {
            traceResult = TraceLogUtil.doTrace(supplier, null, method, args, Lists.newArrayList(TraceLogElement.METHOD_FULL_NAME,
                    TraceLogElement.METHOD_ARGS, TraceLogElement.METHOD_RETURN,
                    TraceLogElement.METHOD_EXCEPTION, TraceLogElement.METHOD_COST, TraceLogElement.OUTPUT_LOG_COST), argFilter);
        }
        try {
            //同步方式打印日志（推荐）
            String resultStr = traceResult.getLogResultFuture().get();
            LOGGER.info("trace info:{}", resultStr.substring(0, Math.min(resultStr.length(), RESULT_LOG_LENGTH_LIMIT)));
            Throwable exp = traceResult.getException();
            if (exp != null) {
                throw exp;
            }
            return traceResult.getData();
        } finally {
            if (addTrace) {
                MDC.remove(TraceConstants.TRACE_ID_KEY);
            }
        }
    }

}
