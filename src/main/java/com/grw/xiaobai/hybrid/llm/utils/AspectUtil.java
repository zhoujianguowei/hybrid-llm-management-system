package com.grw.xiaobai.hybrid.llm.utils;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.annotation.Annotation;

public class AspectUtil {
    public static <T extends Annotation> T getMethodOrTypeAnnotation(ProceedingJoinPoint joinPoint, Class<T> annotationClz) {
        if (joinPoint.getSignature() instanceof MethodSignature) {
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            T annotation = methodSignature.getMethod().getAnnotation(annotationClz);
            if (annotation != null) {
                return annotation;
            }
            return methodSignature.getMethod().getDeclaringClass().getAnnotation(annotationClz);
        }
        return null;
    }
}
