package com.grw.xiaobai.hybrid.llm.service.impl;

import com.grw.xiaobai.hybrid.llm.constant.ErrorCodeConstants;
import com.grw.xiaobai.hybrid.llm.controller.LoginController;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.entity.user.UserContext;
import com.grw.xiaobai.hybrid.llm.enums.AccountStatusEnum;
import com.grw.xiaobai.hybrid.llm.manager.UserContextManager;
import com.grw.xiaobai.hybrid.llm.service.LoginService;
import com.grw.xiaobai.hybrid.llm.service.SessionService;
import com.grw.xiaobai.hybrid.llm.service.UserService;
import com.grw.xiaobai.hybrid.llm.service.VerifyService;
import com.grw.xiaobai.hybrid.llm.utils.ThreadLocalContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Service
public class VerifyServiceImpl implements VerifyService {
    private static final String SESSION_HEADER = "X-Session-Id";
    private static final String USER_INFO_KEY = "currentUser";

    @Resource
    private SessionService sessionService;
    @Resource
    private LoginService loginService;
    @Resource
    private UserService userService;
    @Resource
    private UserContextManager userContextManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(VerifyServiceImpl.class);

    @Override
    public Object verifyIdentity(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return ResultModel.fail(ErrorCodeConstants.UNAUTHORIZED, "common.no_request_context", "No request context");
        }

        HttpServletRequest request = attrs.getRequest();
        HttpServletResponse response = attrs.getResponse();
        String path = request.getRequestURI();

        if (LoginController.class.isAssignableFrom(joinPoint.getTarget().getClass())) {
            try {
                return joinPoint.proceed();
            } finally {
                ThreadLocalContext.clear();
            }
        }

        //ignore register-guest, request-unblock, or acquire media

        if (path.endsWith("/file/user/request-unblock") || path.endsWith("/file/user/register-guest")
                || path.matches(".*/chat/media/\\d{10}_\\d+_[a-zA-Z]{8}\\.[a-zA-Z0-9]+")) {
            try {
                return joinPoint.proceed();
            } finally {
                ThreadLocalContext.clear();
            }
        }

        String sessionId = getSessionId(request);
        if (sessionId == null || !sessionService.validateSession(sessionId)) {
            LOGGER.warn("invalid or expired session, path={}, sessionId={}", path, sessionId);
            if (response != null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
            return ResultModel.fail(ErrorCodeConstants.UNAUTHORIZED, "login.session_invalid", "Invalid or expired session, please log in again");
        }
        String username = loginService.getCurrentUsername(sessionId);
        UserInfo currentUser = userService.getUserByUsername(username);
        if (currentUser == null) {
            if (response != null) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
            return ResultModel.fail(ErrorCodeConstants.UNAUTHORIZED, "user.not_found", "User does not exist");
        }
        if (AccountStatusEnum.block.equals(currentUser.getStatus())) {
            if (response != null) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
            return ResultModel.fail(ErrorCodeConstants.USER_BLOCKED, "login.user_blocked", "User is blocked, contact admin to unblock");
        }
        Object[] args = joinPoint.getArgs();

        try {
            ThreadLocalContext.put(USER_INFO_KEY, currentUser);
            currentUser.setSessionId(sessionId);
            UserContext userContext = userContextManager.loadUserContextCurrentUser();
            currentUser.setUserContext(userContext);
            return joinPoint.proceed(args);
        } finally {
            ThreadLocalContext.remove(USER_INFO_KEY);

        }
    }

    private String getSessionId(HttpServletRequest request) {
        String sessionId = request.getHeader(SESSION_HEADER);
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = request.getParameter("sessionId");
        }
        return sessionId;
    }
}
