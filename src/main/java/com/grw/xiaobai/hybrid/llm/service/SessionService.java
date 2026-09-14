package com.grw.xiaobai.hybrid.llm.service;

public interface SessionService {
    String createSession(String username);

    boolean validateSession(String sessionId);

    void removeSession(String sessionId);

    void removeSessionsByUsername(String username);

    String getUsername(String sessionId);

    String getUsernameBySessionId(String sessionId);

    int getActiveSessionCount();

    boolean isUserOnline(String username);
}
