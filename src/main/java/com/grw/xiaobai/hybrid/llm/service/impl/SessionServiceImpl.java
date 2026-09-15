package com.grw.xiaobai.hybrid.llm.service.impl;

import com.grw.xiaobai.hybrid.llm.constant.ThreadPoolConstants;
import com.grw.xiaobai.hybrid.llm.service.SessionService;
import com.grw.xiaobai.hybrid.llm.entity.user.SessionInfo;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class SessionServiceImpl implements SessionService {
    private static final long CLEAN_INTERVAL_MINUTES = 10;
    private static final long SESSION_TIMEOUT_MILLIS = TimeUnit.HOURS.toMillis(12);

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    @Resource(name = ThreadPoolConstants.SESSION_CLEAN_SCHEDULED_THREAD_POOL)
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::cleanExpiredSessions,
                CLEAN_INTERVAL_MINUTES, CLEAN_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    public String createSession(String username) {
        String sessionId = java.util.UUID.randomUUID().toString();
        sessions.put(sessionId, new SessionInfo(username, System.currentTimeMillis()));
        return sessionId;
    }

    public boolean validateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }
        SessionInfo info = sessions.get(sessionId);
        if (info == null) {
            return false;
        }
        if (isExpired(info)) {
            sessions.remove(sessionId);
            return false;
        }
        info.activate();
        return true;
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public void removeSessionsByUsername(String username) {
        sessions.entrySet().removeIf(entry -> entry.getValue().getUsername().equals(username));
    }

    public String getUsername(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        return info != null ? info.getUsername() : null;
    }

    public String getUsernameBySessionId(String sessionId) {
        return getUsername(sessionId);
    }

    private boolean isExpired(SessionInfo info) {
        long elapsed = System.currentTimeMillis() - info.getActivateTime();
        return elapsed > SESSION_TIMEOUT_MILLIS;
    }

    private void cleanExpiredSessions() {
        Iterator<Map.Entry<String, SessionInfo>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SessionInfo> entry = iterator.next();
            if (System.currentTimeMillis() - entry.getValue().getActivateTime() > SESSION_TIMEOUT_MILLIS) {
                iterator.remove();
            }
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public boolean isUserOnline(String username) {
        for (Map.Entry<String, SessionInfo> entry : sessions.entrySet()) {
            SessionInfo info = entry.getValue();
            if (info.getUsername().equals(username) && !isExpired(info)) {
                return true;
            }
        }
        return false;
    }
}
