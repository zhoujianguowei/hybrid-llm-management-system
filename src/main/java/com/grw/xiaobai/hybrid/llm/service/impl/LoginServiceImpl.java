package com.grw.xiaobai.hybrid.llm.service.impl;

import com.grw.xiaobai.hybrid.llm.constant.ErrorCodeConstants;
import com.grw.xiaobai.hybrid.llm.exception.BusinessLogicException;
import com.grw.xiaobai.hybrid.llm.service.LoginService;
import com.grw.xiaobai.hybrid.llm.service.SessionService;
import com.grw.xiaobai.hybrid.llm.service.UserService;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.enums.AccountStatusEnum;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LoginServiceImpl implements LoginService {
    private static final int DELAY_THRESHOLD = 3;
    // 限制失败尝试记录数量与存活时间，避免任意用户名刷入导致内存无限增长
    private final Cache<String, Integer> failedAttempts = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    @Resource
    private UserService userService;
    @Resource
    private SessionService sessionService;

    public ResultModel<String> login(String username, String password) {
        UserInfo user = userService.validatePassword(username, password);
        if (user == null) {
            // asMap().merge 保证自增原子性，避免并发失败登录丢失计数
            int newCount = failedAttempts.asMap().merge(username, 1, Integer::sum);
            if (newCount >= DELAY_THRESHOLD) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            throw BusinessLogicException.builder().msg("Invalid username or password").msgKey("login.credential_error").build();
        }

        failedAttempts.invalidate(username);

        if (AccountStatusEnum.block.equals(user.getStatus())) {
            return ResultModel.fail(ErrorCodeConstants.USER_BLOCKED, "login.user_blocked", "User is blocked, contact admin to unblock");
        }

        if (!UserRoleEnum.admin.equals(user.getRole()) && user.getRegisterEnd() != null
                && System.currentTimeMillis() > user.getRegisterEnd()) {
            userService.updateUserStatus(username, AccountStatusEnum.block);
            return ResultModel.fail(ErrorCodeConstants.USER_BLOCKED, "login.user_blocked", "User is blocked, contact admin to unblock");
        }

        userService.updateLastLoginTime(username);

        String sessionId = sessionService.createSession(username);

        if (UserRoleEnum.admin.equals(user.getRole()) && user.getForceChangePassword()) {
            ResultModel<String> result = new ResultModel<>();
            result.setCode(ErrorCodeConstants.FORCE_CHANGE_PASSWORD);
            result.setSuccess(true);
            result.setData(sessionId);
            result.setMsg("Password change required");
            result.setMsgKey("login.force_change_password");
            return result;
        }

        ResultModel<String> result = new ResultModel<>();
        result.setCode(ErrorCodeConstants.SUCCESS);
        result.setSuccess(true);
        result.setData(sessionId);
        result.setMsg("Login successful");
        result.setMsgKey("login.success");
        return result;
    }

    public void logout(String sessionId) {
        sessionService.removeSession(sessionId);
    }

    public ResultModel<Boolean> forceChangePassword(String sessionId, String newPassword) {
        String username = sessionService.getUsername(sessionId);
        if (username == null) {
            return ResultModel.fail(ErrorCodeConstants.UNAUTHORIZED, "login.session_expired", "Session has expired");
        }

        String confirmPassword = newPassword;
        userService.changePassword(username, newPassword, confirmPassword);
        return ResultModel.OK(true);
    }

    public boolean isSessionValid(String sessionId) {
        return sessionId != null && sessionService.validateSession(sessionId);
    }

    public String getCurrentUsername(String sessionId) {
        return sessionService.getUsername(sessionId);
    }

    public ResultModel<String> validateSession(String sessionId) {
        if (sessionService.validateSession(sessionId)) {
            String username = sessionService.getUsername(sessionId);
            return ResultModel.OK(username);
        }
        return ResultModel.fail(ErrorCodeConstants.UNAUTHORIZED, "login.session_invalid", "Invalid or expired session");
    }
}
