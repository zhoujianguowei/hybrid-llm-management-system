package com.grw.xiaobai.hybrid.llm.service.impl;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatMessage;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSession;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSessionSummary;
import com.grw.xiaobai.hybrid.llm.entity.user.UserContext;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.constant.SystemConstants;
import com.grw.xiaobai.hybrid.llm.service.ChatSessionService;
import com.grw.xiaobai.hybrid.llm.utils.image.MediaCacheHandler;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatSessionServiceImpl implements ChatSessionService {
    private static final String CHAT_DIR = FilePathConstants.CHAT_DIR;

    static {
        FileUtil.mkdir(CHAT_DIR);
    }

    public List<ChatSessionSummary> getSessionList(String username, int offset, int limit, Integer type, int[] count) {
        List<ChatSessionSummary> all = loadAllSessionSummaries(username);
        List<ChatSessionSummary> filtered;
        if (type != null && type == 1) {
            filtered = all.stream().filter(s -> s.getType() != null && s.getType() == 1).collect(java.util.stream.Collectors.toList());
        } else {
            filtered = all.stream().filter(s -> s.getType() == null || s.getType() == 0).collect(java.util.stream.Collectors.toList());
        }
        count[0] = filtered.size();
        int end = Math.min(offset + limit, filtered.size());
        return offset >= end ? Lists.newArrayList() : filtered.subList(offset, end);
    }

    public int getSessionCount(String username, Integer type) {
        List<ChatSessionSummary> all = loadAllSessionSummaries(username);
        if (type == null) {
            return all.size();
        }
        if (type != null && type == 1) {
            return (int) all.stream().filter(s -> s.getType() != null && s.getType() == 1).count();
        }
        return (int) all.stream().filter(s -> s.getType() == null || s.getType() == 0).count();
    }

    public ChatSession createSession(String username, String apiConfigId, String apiConfigName, String modelName) {
        ensureUserChatDirExists(username);

        ChatSession session = new ChatSession();
        long timestamp = System.currentTimeMillis();
        String chatId = timestamp + "_" + UUID.randomUUID().toString().replace("-", "");

        session.setChatId(chatId);
        session.setTitle(SystemConstants.DEFAULT_SESSION_TITLE_KEY);
        session.setCreatedAt(timestamp);
        session.setUpdatedAt(timestamp);
        session.setApiConfigId(apiConfigId);
        session.setApiConfigName(apiConfigName);
        session.setModelName(modelName);
        session.setMessages(new ArrayList<>());

        saveSession(username, session);
        return session;
    }

    public ChatSession loadSession(String username, String chatId) {
        File userChatDir = new File(CHAT_DIR, username);
        if (!userChatDir.exists()) {
            return null;
        }

        File[] files = userChatDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.getName().equals(chatId + ".json")) {
                return loadSessionFromFile(file);
            }
        }

        return null;
    }

    public void saveSession(String username, ChatSession session) {
        ensureUserChatDirExists(username);

        File userChatDir = new File(CHAT_DIR, username);
        File[] existingFiles = userChatDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (existingFiles != null) {
            for (File file : existingFiles) {
                if (file.getName().equals(session.getChatId() + ".json")) {
                    FileUtil.del(file);
                    break;
                }
            }
        }
        String fileName = session.getChatId() + ".json";
        File sessionFile = new File(userChatDir, fileName);

        String jsonStr = JSON.toJSONString(session, SerializerFeature.PrettyFormat);
        FileUtil.writeString(jsonStr, sessionFile, StandardCharsets.UTF_8);
        log.debug("Chat session saved: {}", fileName);
    }

    public boolean deleteSession(String username, String chatId) {
        File userChatDir = new File(CHAT_DIR, username);
        if (!userChatDir.exists()) {
            return false;
        }

        File[] files = userChatDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return false;
        }

        for (File file : files) {
            if (file.getName().equals(chatId + ".json")) {
                ChatSession session = loadSessionFromFile(file);
                if (session != null && session.getMessages() != null) {
                    for (ChatMessage msg : session.getMessages()) {
                        MediaCacheHandler.cleanupMediaFiles(msg.getImageUrlList(), msg.getVideoUrlList(), msg.getAudioUrlList());
                    }
                }
                boolean deleted = FileUtil.del(file);
                if (deleted) {
                    log.info("Chat session deleted: {}", file.getName());
                }
                return deleted;
            }
        }

        return false;
    }

    public void addMessage(String username, String chatId, ChatMessage message) {
        ChatSession session = loadSession(username, chatId);
        if (session != null) {
            message.setTimestamp(System.currentTimeMillis());
            session.getMessages().add(message);
            session.setUpdatedAt(System.currentTimeMillis());
            saveSession(username, session);
        }
    }

    public void updateSession(String username, String chatId, ChatSession update) {
        ChatSession session = loadSession(username, chatId);
        if (session != null) {
            Optional.ofNullable(update.getTitle()).ifPresent(session::setTitle);
            Optional.ofNullable(update.getModelName()).ifPresent(session::setModelName);
            Optional.ofNullable(update.getType()).ifPresent(session::setType);
            Optional.ofNullable(update.getStats()).ifPresent(session::setStats);
            Optional.ofNullable(update.getApiConfigId()).ifPresent(session::setApiConfigId);
            Optional.ofNullable(update.getApiConfigName()).ifPresent(session::setApiConfigName);
            Optional.ofNullable(update.getThinkingMode()).ifPresent(session::setThinkingMode);
            Optional.ofNullable(update.getChatRuntimeConfig()).ifPresent(session::setChatRuntimeConfig);
            session.setUpdatedAt(System.currentTimeMillis());
            saveSession(username, session);
        }
    }

    public void deleteAllSessionsByUsername(String username) {
        File userChatDir = new File(CHAT_DIR, username);
        if (!userChatDir.exists() || !userChatDir.isDirectory()) {
            return;
        }

        File[] files = userChatDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            ChatSession session = loadSessionFromFile(file);
            deleteSession(username, session.getChatId());
        }
        FileUtil.del(userChatDir);
        log.info("All chat sessions deleted for user: {}", username);
    }

    public int cleanupExpiredChatSessions() {
        File chatRootDir = new File(CHAT_DIR);
        if (!chatRootDir.exists() || !chatRootDir.isDirectory()) {
            return 0;
        }
        File[] userChatFiles = new File(CHAT_DIR).listFiles(File::isDirectory);
        if (userChatFiles == null) {
            return 0;
        }

        int deletedCount = 0;
        for (File userChatDir : userChatFiles) {
            String username = userChatDir.getName();
            int retentionDays = getUserSessionRetentionDays(username);
            long cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
            File[] files = userChatDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    ChatSessionSummary summary = loadSessionSummary(file);
                    if (summary != null && summary.getType() != null && summary.getType() == 1) {
                        continue;
                    }
                    if (summary != null && summary.getCreatedAt() < cutoffTime) {
                        ChatSession session = loadSessionFromFile(file);
                        if (session != null && session.getMessages() != null) {
                            for (ChatMessage msg : session.getMessages()) {
                                MediaCacheHandler.cleanupMediaFiles(msg.getImageUrlList(), msg.getVideoUrlList(), msg.getAudioUrlList());
                            }
                        }
                        if (FileUtil.del(file)) {
                            deletedCount++;
                            log.info("Deleted expired session: {}||userRootDir={}||retentionDays={}", file.getName(), username, retentionDays);
                        }
                    }
                }
            }
        }
        return deletedCount;
    }

    private int getUserSessionRetentionDays(String username) {
        String userConfigDir = FilePathConstants.USER_CONFIG_DIR;
        File configFile = new File(userConfigDir, username + "_running_param.json");
        if (configFile.exists() && configFile.isFile()) {
            try {
                String json = FileUtil.readString(configFile, StandardCharsets.UTF_8);
                UserContext userContext = JSON.parseObject(json, UserContext.class);
                if (userContext != null && userContext.getChatContext() != null) {
                    Integer retentionDays = userContext.getChatContext().getSessionRetentionDays();
                    if (retentionDays != null && retentionDays > 0) {
                        return retentionDays;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to read user context for {}: {}", username, e.getMessage());
            }
        }
        return 365;
    }

    private void ensureUserChatDirExists(String username) {
        File userChatDir = new File(CHAT_DIR, username);
        if (!userChatDir.exists()) {
            userChatDir.mkdirs();
        }
    }

    private List<ChatSessionSummary> loadAllSessionSummaries(String username) {
        File userChatDir = new File(CHAT_DIR, username);
        if (!userChatDir.exists()) {
            return new ArrayList<>();
        }
        File[] files = userChatDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return new ArrayList<>();
        }

        List<ChatSessionSummary> summaries = new ArrayList<>();
        for (File file : files) {
            try {
                ChatSessionSummary summary = loadSessionSummary(file);
                if (summary != null) {
                    summaries.add(summary);
                }
            } catch (Exception e) {
                log.warn("Failed to load session summary from: {}", file.getName());
            }
        }

        summaries.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return summaries;
    }

    private ChatSessionSummary loadSessionSummary(File sessionFile) {
        String json = FileUtil.readString(sessionFile, StandardCharsets.UTF_8);
        ChatSessionSummary summary = JSON.parseObject(json, ChatSessionSummary.class);

        List<ChatMessage> messages = summary.getMessages();
        summary.setMessageCount(messages != null ? messages.size() : 0);

        if (messages != null && !messages.isEmpty()) {
            ChatMessage lastMsg = messages.get(messages.size() - 1);
            String lastContent = lastMsg.getContent();
            summary.setLastMessage(lastContent != null && lastContent.length() > 50 ? lastContent.substring(0, 50) + "..." : lastContent);
        }

        return summary;
    }

    private ChatSession loadSessionFromFile(File sessionFile) {
        String json = FileUtil.readString(sessionFile, StandardCharsets.UTF_8);
        return JSON.parseObject(json, ChatSession.class);
    }
}