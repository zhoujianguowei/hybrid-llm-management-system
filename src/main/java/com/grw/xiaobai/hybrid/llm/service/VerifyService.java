package com.grw.xiaobai.hybrid.llm.service;

import org.aspectj.lang.ProceedingJoinPoint;

public interface VerifyService {
    Object verifyIdentity(ProceedingJoinPoint joinPoint) throws Throwable;
}
