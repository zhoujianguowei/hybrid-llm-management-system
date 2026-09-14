package com.grw.xiaobai.hybrid.llm.aspect;

import com.grw.xiaobai.hybrid.llm.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Aspect
@Component
@Order(20)
public class UserPermissionAspect {

    @Resource
    private UserService userService;

    @Around("@annotation(com.grw.xiaobai.hybrid.llm.annotation.UserPermission) || @within(com.grw.xiaobai.hybrid.llm.annotation.UserPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        return userService.checkPermission(joinPoint);
    }

}