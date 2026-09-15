package com.grw.xiaobai.hybrid.llm.aspect;

import com.grw.xiaobai.hybrid.llm.service.FilePermissionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Aspect
@Component
@Order(100)
public class FilePermissionAspect {

    @Resource
    private FilePermissionService permissionService;

    @Around("@annotation(com.grw.xiaobai.hybrid.llm.annotation.FilePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        return permissionService.checkPermission(joinPoint);
    }
}
