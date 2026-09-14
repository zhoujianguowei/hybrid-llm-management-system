package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.entity.file.FileInfo;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.entity.file.FilePermissionRule;
import com.grw.xiaobai.hybrid.llm.entity.file.FilePermissionTree;
import org.aspectj.lang.ProceedingJoinPoint;

public interface FilePermissionService extends UserService.UserDeleteListener {
    boolean checkPermission(String path, int mask);

    boolean isPathExplicitlyProtected(String path);

    int computeFilePermission(FileInfo info, UserInfo user);

    int computePathPermission(String path);

    FilePermissionTree listAll();

    void savePathRule(FilePermissionRule rule);

    void deletePathRule(String path);
    Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable;
}
