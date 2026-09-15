package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;

public interface LoginService {
    ResultModel<String> login(String username, String password);

    void logout(String sessionId);

    ResultModel<Boolean> forceChangePassword(String sessionId, String newPassword);

    boolean isSessionValid(String sessionId);

    String getCurrentUsername(String sessionId);

    ResultModel<String> validateSession(String sessionId);
}
