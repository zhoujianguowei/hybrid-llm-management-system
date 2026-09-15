package com.grw.xiaobai.hybrid.llm.manager;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSession;
import com.grw.xiaobai.hybrid.llm.entity.file.FileInfo;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.entity.user.UserContext;
import com.grw.xiaobai.hybrid.llm.handler.ChatWebSocketHandler;
import com.grw.xiaobai.hybrid.llm.service.ChatSessionService;
import com.grw.xiaobai.hybrid.llm.service.CoreUserService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserContextManager {
    private static final String USER_CONFIG_DIR = FilePathConstants.USER_CONFIG_DIR;
    private static final long CACHE_EXPIRE_MINUTES = 60;

    private LoadingCache<String, UserContext> contextCache;

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    @Resource
    private CoreUserService coreUserService;
    @Resource
    private ChatSessionService chatSessionService;
    private static final Logger LOGGER = LoggerFactory.getLogger(UserContextManager.class);

    @PostConstruct
    public void init() {
        this.contextCache = CacheBuilder.newBuilder()
                .expireAfterAccess(CACHE_EXPIRE_MINUTES, java.util.concurrent.TimeUnit.MINUTES)
                .removalListener(this::onCacheRemoval)
                .build(new CacheLoader<String, UserContext>() {
                    @Override
                    public UserContext load(String username) {
                        return loadFromDiskOrConstruct(username);
                    }
                });
    }

    @PreDestroy
    public void preDestroy() {
        contextCache.asMap().forEach((username, userContext) -> {
            if (userContext != null) {
                saveToFile(username, userContext);
            }
        });
    }

    private void onCacheRemoval(RemovalNotification<String, UserContext> notification) {
        if (notification.getCause() == com.google.common.cache.RemovalCause.EXPIRED
                || notification.getCause() == com.google.common.cache.RemovalCause.REPLACED) {
            UserContext userContext = notification.getValue();
            if (userContext != null) {
                saveToFile(notification.getKey(), userContext);
            }
        }
    }

    private void saveToFile(String username, UserContext userContext) {
        try {
            File configFile = getUserConfigFile(username);
            FileUtil.mkdir(configFile.getParentFile());
            String json = JSON.toJSONString(userContext, SerializerFeature.PrettyFormat);
            FileUtil.writeString(json, configFile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Failed to save user context to file for user={}", username, e);
        }
    }

    public UserContext loadUserContextCurrentUser() {
        UserInfo userInfo = coreUserService.getCurrentUser();
        String username = userInfo.getUsername();
        try {
            UserContext userContext = contextCache.get(username);
            userContext.getChatContext().setActiveChatIdList(chatWebSocketHandler.getActiveChatIdsByUsername(username));
            userInfo.setUserContext(userContext);
            return userContext;
        } catch (ExecutionException e) {
            LOGGER.error("Failed to load user context for user={}", username, e);
            throw new RuntimeException("Failed to load user context", e);
        }
    }

    public void saveUserContext(UserContext userContext) {
        UserContext currentUserContext = loadUserContextCurrentUser();
        UserContext.ChatContext chatContext = currentUserContext.getChatContext();
        chatContext.setLastPinnedSessionOpenedStatus(userContext.getChatContext().getLastPinnedSessionOpenedStatus());
        chatContext.setSidebarCollapsed(userContext.getChatContext().getSidebarCollapsed());
        chatContext.setSessionRetentionDays(userContext.getChatContext().getSessionRetentionDays());
        chatContext.setSendShortcutKey(userContext.getChatContext().getSendShortcutKey());
        chatContext.setAttachReasoningContent(userContext.getChatContext().isAttachReasoningContent());
        chatContext.setSystemPrompt(userContext.getChatContext().getSystemPrompt());
    }

    private UserContext loadFromDiskOrConstruct(String username) {
        File configFile = getUserConfigFile(username);
        if (configFile.exists() && configFile.isFile()) {
            String json = FileUtil.readString(configFile, StandardCharsets.UTF_8);
            UserContext userContext = JSON.parseObject(json, UserContext.class);
            if (userContext == null) {
                userContext = new UserContext();
            }
            if (userContext.getChatContext() == null) {
                userContext.setChatContext(new UserContext.ChatContext());
            }
            if (userContext.getFileManagerContext() == null) {
                userContext.setFileManagerContext(new UserContext.FileManagerContext());
            }
            return userContext;
        }
        return constructUserContext(username);
    }

    private UserContext constructUserContext(String username) {
        UserContext userContext = new UserContext();
        UserContext.ChatContext chatContext = new UserContext.ChatContext();
        chatContext.setActiveChatIdList(chatWebSocketHandler.getActiveChatIdsByUsername(username));
        userContext.setChatContext(chatContext);
        UserContext.FileManagerContext fileManagerContext = new UserContext.FileManagerContext();
        userContext.setFileManagerContext(fileManagerContext);
        ChatSession chatSession = chatSessionService.createSession(username, "", "", "");
        LOGGER.info("first create blank session for user={}", username);
        userContext.getChatContext().setLastOpenedChatId(chatSession.getChatId());
        saveToFile(username, userContext);
        return userContext;
    }

    private File getUserConfigFile(String username) {
        return new File(USER_CONFIG_DIR, username + "_running_param.json");
    }

    public void setLastVisitedPath(String path) {
        UserContext userContext = loadUserContextCurrentUser();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setPath(path);
        userContext.getFileManagerContext().setLastVisitedFileDir(fileInfo);
    }

    public void clearLastVisitedFileDir() {
        UserContext userContext = loadUserContextCurrentUser();
        userContext.getFileManagerContext().setLastVisitedFileDir(null);
    }

    public void deleteUserContext(String username) {
        contextCache.invalidate(username);
        File contextFile = getUserConfigFile(username);
        if (contextFile.exists()) {
            boolean deleted = contextFile.delete();
            if (!deleted) {
                LOGGER.warn("Failed to delete user context file for user={}, path={}", username, contextFile.getAbsolutePath());
            }
        }
    }
}
