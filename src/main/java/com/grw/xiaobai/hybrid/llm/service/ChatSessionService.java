package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.entity.chat.ChatMessage;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSession;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSessionSummary;

import java.util.List;

public interface ChatSessionService {
    List<ChatSessionSummary> getSessionList(String username, int offset, int limit, Integer type, int[] count);

    int getSessionCount(String username, Integer type);

    ChatSession createSession(String username, String apiConfigId, String apiConfigName, String modelName);

    ChatSession loadSession(String username, String chatId);

    void saveSession(String username, ChatSession session);

    boolean deleteSession(String username, String chatId);

    void addMessage(String username, String chatId, ChatMessage message);

    void updateSession(String username, String chatId, ChatSession update);

    void deleteAllSessionsByUsername(String username);

    int cleanupExpiredChatSessions();
}
