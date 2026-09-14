package com.grw.xiaobai.hybrid.llm.aspect;

import com.grw.xiaobai.hybrid.llm.service.VerifyService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Aspect
@Component
@Order(10)
public class SessionAspect {

    @Resource
    private VerifyService verifyService;


    @Around("within(com.grw.xiaobai.hybrid.llm.controller.*)")
    public Object validateSession(ProceedingJoinPoint joinPoint) throws Throwable {
        return verifyService.verifyIdentity(joinPoint);
    }


}
