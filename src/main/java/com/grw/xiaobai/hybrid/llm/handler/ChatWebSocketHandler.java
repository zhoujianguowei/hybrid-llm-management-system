package com.grw.xiaobai.hybrid.llm.handler;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.utils.converter.OrikaBeanConverter;
import com.grw.xiaobai.hybrid.llm.constant.SystemConstants;
import com.grw.xiaobai.hybrid.llm.constant.ThreadPoolConstants;
import com.grw.xiaobai.hybrid.llm.constant.TraceConstants;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatMessage;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatModel;
import com.grw.xiaobai.hybrid.llm.request.ChatRequest;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSession;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSessionConfig;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatStats;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiLLMConfig;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiStats;
import com.grw.xiaobai.hybrid.llm.entity.chat.WebSocketMessage;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatRuntimeConfig;
import com.grw.xiaobai.hybrid.llm.entity.chat.RunningChatApiParamWrapper;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import com.grw.xiaobai.hybrid.llm.manager.ChatSessionConfigManager;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatHistory;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiClient;
import com.grw.xiaobai.hybrid.llm.service.ChatService;
import com.grw.xiaobai.hybrid.llm.service.LoginService;
import com.grw.xiaobai.hybrid.llm.service.UserService;
import com.grw.xiaobai.hybrid.llm.task.OpenApiModelScanTask;
import com.grw.xiaobai.hybrid.llm.utils.ExceptionUtil;
import com.grw.xiaobai.hybrid.llm.utils.LockUtil;
import com.grw.xiaobai.hybrid.llm.utils.ThreadLocalContext;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Resource
    private ChatService chatService;

    @Resource
    private LoginService loginService;

    @Resource
    private UserService userService;

    @Resource
    private OpenApiModelScanTask openApiModelScanTask;

    @Resource(name = ThreadPoolConstants.CHAT_TASK_EXECUTOR_NAME)
    private ExecutorService chatTaskExecutor;

    @Resource
    private ChatSessionConfigManager sessionConfigManager;

    private final Map<String, RunningChatApiParamWrapper> chatIdRunningChatApiParamWrapperMap = new ConcurrentHashMap<>();
    private final Map<String, ChatHistory> chatId2ChatHistoryMap = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> chatId2ChatMessagesMap = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Object> sendLockMap = new WeakHashMap<>();
    private ReentrantLock[] LOCKS = new ReentrantLock[63];

    @Resource(name = ThreadPoolConstants.HEARTBEAT_SCHEDULED_THREAD_POOL_NAME)
    private ScheduledExecutorService heartbeatExecutor;

    private static final long HEARTBEAT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long INACTIVE_TIMEOUT_MS = TimeUnit.HOURS.toMillis(10);
    private static final CloseStatus SESSION_EXPIRED = new CloseStatus(1008, "Session Expired");

    @PostConstruct
    public void init() {
        for (int i = 0; i < LOCKS.length; i++) {
            LOCKS[i] = new ReentrantLock();
        }
    }


    private ReentrantLock getLock(int hashCode) {
        return LOCKS[(hashCode & 0x7FFFFFFF) % LOCKS.length];
    }

    private Object getSendLock(WebSocketSession session) {
        synchronized (sendLockMap) {
            return sendLockMap.computeIfAbsent(session, key -> new Object());
        }
    }

    private void removeSendLock(WebSocketSession session) {
        synchronized (sendLockMap) {
            sendLockMap.remove(session);
        }
    }

    private void sendHeartbeatPing(String chatId, WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            return;
        }
        Object sendLock = getSendLock(session);
        synchronized (sendLock) {
            try {
                session.sendMessage(new PingMessage(ByteBuffer.wrap("ping".getBytes(StandardCharsets.UTF_8))));
            } catch (IOException e) {
                log.debug("Heartbeat ping failed for chatId={}", chatId, e);
            }
        }
    }

    private String extractChatId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] parts = param.split("=");
                    if (parts.length == 2 && "chatId".equals(parts[0])) {
                        return URLUtil.decode(parts[1], StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Extract chatId error", e);
        }
        return null;
    }

    private String extractSessionId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] parts = param.split("=");
                    if (parts.length == 2 && "sessionId".equals(parts[0])) {
                        return URLUtil.decode(parts[1], StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Extract sessionId error", e);
        }
        return null;
    }

    private void startHeartbeat(String chatId, WebSocketSession session) {
        RunningChatApiParamWrapper wrapper = chatIdRunningChatApiParamWrapperMap.get(chatId);
        if (wrapper != null && wrapper.getHeartbeatFuture() != null) {
            wrapper.getHeartbeatFuture().cancel(false);
        }
        ScheduledFuture<?> future = heartbeatExecutor.scheduleWithFixedDelay(() -> {
            try {
                checkHeartbeat(chatId, session);
            } catch (Exception e) {
                log.warn("Heartbeat check error for chatId={}", chatId, e);
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        wrapper.setHeartbeatFuture(future);
    }

    private void checkHeartbeat(String chatId, WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            return;
        }
        RunningChatApiParamWrapper wrapper = chatIdRunningChatApiParamWrapperMap.get(chatId);
        if (wrapper == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String sessionId = wrapper.getSessionId();
        if (sessionId != null && !loginService.isSessionValid(sessionId)) {
            log.info("Session expired for chatId={}, closing connection", chatId);
            closeSession(chatId, session, SESSION_EXPIRED);
            return;
        }
        if (now - wrapper.getLastActiveTime() > INACTIVE_TIMEOUT_MS) {
            log.info("Inactive timeout for chatId={}, closing connection", chatId);
            closeSession(chatId, session, CloseStatus.NORMAL);
            return;
        }
        sendHeartbeatPing(chatId, session);
        if (wrapper.getLatestAssistantMessage() != null
                && ChatMessage.MessageStatus.isInRunning(wrapper.getLatestAssistantMessage().getStatus())) {
            wrapper.setLastActiveTime(now);
        }
    }

    private void closeSession(String chatId, WebSocketSession session, CloseStatus status) {
        RunningChatApiParamWrapper wrapper = chatIdRunningChatApiParamWrapperMap.get(chatId);
        if (wrapper != null && wrapper.getHeartbeatFuture() != null) {
            wrapper.getHeartbeatFuture().cancel(false);
        }
        removeChatContext(chatId);
        if (session != null && session.isOpen()) {
            try {
                WebSocketMessage msg = status == SESSION_EXPIRED
                        ? WebSocketMessage.sessionExpiry("chat.session_expired", "Session expired, please refresh and login again", null)
                        : WebSocketMessage.sessionExpiry("chat.connection_timeout", "Connection timeout, please refresh the page", null);
                sendMessage(session, msg, chatId);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                session.close(status);
            } catch (IOException e) {
                log.warn("Close session error", e);
            }
        }
    }

    private void removeChatContext(String chatId) {
        chatId2ChatHistoryMap.remove(chatId);
        chatId2ChatMessagesMap.remove(chatId);
        chatIdRunningChatApiParamWrapperMap.remove(chatId);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        String chatId = extractChatId(session);
        String sessionId = extractSessionId(session);
        if (StringUtils.isBlank(chatId)) {
            log.warn("chatId is blank on connection");
            session.close(SESSION_EXPIRED);
            return;
        }
        if (StringUtils.isBlank(sessionId) || !loginService.isSessionValid(sessionId)) {
            log.warn("sessionId invalid on connection: {}", sessionId);
            session.close(SESSION_EXPIRED);
            return;
        }
        RunningChatApiParamWrapper wrapper = chatIdRunningChatApiParamWrapperMap.computeIfAbsent(chatId, k -> {
            RunningChatApiParamWrapper newWrapper = new RunningChatApiParamWrapper();
            return newWrapper;
        });
        ReentrantLock lock = getLock(chatId.hashCode());
        try {
            lock.lock();
            WebSocketSession oldSession = wrapper.getActiveSocketSession();
            wrapper.setActiveSocketSession(session);
            wrapper.setSessionId(sessionId);
            wrapper.setLastActiveTime(System.currentTimeMillis());
            startHeartbeat(chatId, session);
            if (oldSession != null && oldSession.isOpen() && !oldSession.equals(session)) {
                log.info("Close old connection for chatId={}", chatId);
                try {
                    oldSession.close(new CloseStatus(1001, "Replaced by new connection"));
                } catch (IOException e) {
                    log.warn("Close old session error", e);
                }
            }
        } finally {
            lock.unlock();
        }
    }


    private boolean injectCurrentLoginUser(WebSocketSession session, String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            sendError(session, "chat.sessionId_required", "sessionId cannot be empty", null);
            return false;
        }
        if (!loginService.isSessionValid(sessionId)) {
            sendError(session, "chat.sessionId_invalid", "sessionId is invalid, refresh the page and retry", null);
            return false;
        }
        String username = loginService.getCurrentUsername(sessionId);
        UserInfo currentUser = userService.getUserByUsername(username);
        if (currentUser == null) {
            sendError(session, "chat.session_expired", "Session expired, please refresh and login again", null);
            return false;
        }
        currentUser.setSessionId(sessionId);
        ThreadLocalContext.put(TraceConstants.WEB_SOCKET_CURRENT_USER_INFO_KEY, currentUser);
        return true;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JSONObject payload = JSONObject.parseObject(message.getPayload());
            String type = payload.getString("type");
            chatIdRunningChatApiParamWrapperMap.values().forEach(w -> {
                if (w.getActiveSocketSession() != null && w.getActiveSocketSession().equals(session)) {
                    w.setLastActiveTime(System.currentTimeMillis());
                }
            });

            switch (type) {
                case "chat":
                    handleChatRequest(session, payload);
                    break;
                case "stop":
                    handleStopGeneration(session, payload);
                    break;
                case "check_recovery":
                    handleCheckRecovery(session, payload);
                    break;
                default:
                    sendError(session, "chat.unknown_msg_type", "Unknown message type: " + type, Collections.singletonMap("type", type), null);
            }
        } catch (Exception e) {
            log.error("Handle websocket message error", e);
            sendError(session, "chat.handle_msg_failed", "Failed to handle message: " + e.getMessage(), Collections.singletonMap("detail", e.getMessage()), null);
        }
    }

    private ChatHistory initChatHistory(String chatId) {
        ChatHistory chatHistory = new ChatHistory();
        ChatSession chatSession = chatService.loadSession(chatId);
        List<ChatMessage> userChatMessageList = new ArrayList<>();
        if (chatSession != null && CollectionUtils.isNotEmpty(chatSession.getMessages())) {
            List<ChatMessage> chatMessageList = chatSession.getMessages();
            for (ChatMessage chatMessage : chatMessageList) {
                if (chatMessage.getRole().equals("user")) {
                    userChatMessageList.add(chatMessage);
                    List<Pair<String, String>> attachPairList = chatMessage.generateMulPairResult();
                    chatHistory.addMultiUserMessage(chatMessage, attachPairList,
                            Optional.ofNullable(chatMessage.getChatMediaTextList()).orElse(Lists.newArrayList()));
                } else {
                    chatHistory.addAssistantMessage(chatMessage.getContent(), chatMessage.getThinkingContent());
                }
            }
        }
        chatId2ChatMessagesMap.put(chatId, userChatMessageList);
        return chatHistory;
    }

    private void initChatStartDialog(RunningChatApiParamWrapper runningChatApiParamWrapper) {
        runningChatApiParamWrapper.getStopFlag().set(false);
        runningChatApiParamWrapper.setLatestAssistantMessage(null);
        runningChatApiParamWrapper.setLatestThinkingContent(null);
        runningChatApiParamWrapper.setLatestContent(null);
        runningChatApiParamWrapper.setOpenApiClient(null);
    }

    private int checkSupportMulti(List<ChatMessage> userChatMessageList, ChatModel chatModel) {
        for (ChatMessage chatMessage : userChatMessageList) {
            List<String> imageUrlList = chatMessage.getImageUrlList();
            List<String> videoUrlList = chatMessage.getVideoUrlList();
            List<String> audioUrlList = chatMessage.getAudioUrlList();
            if (CollectionUtils.isNotEmpty(imageUrlList)) {
                if (!chatModel.supportImage()) {
                    return ChatModel.IMAGE_MASK;
                }
            }
            if (CollectionUtils.isNotEmpty(videoUrlList)) {
                if (!chatModel.supportVideo()) {
                    return ChatModel.VIDEO_MASK;
                }
            }
            if (CollectionUtils.isNotEmpty(audioUrlList)) {
                if (!chatModel.supportAudio()) {
                    return ChatModel.AUDIO_MASK;
                }
            }
        }
        return 0;
    }

    private void doChat(WebSocketSession session, ChatRequest request) {
        String username = userService.getCurrentUser().getUsername();
        String chatId = request.getChatId();
        if (StringUtils.isBlank(chatId)) {
            log.warn("chatId is blank");
            sendMessage(session, WebSocketMessage.error("chat.chatId_required", "chatId cannot be empty"), chatId);
            return;
        }
        ChatModel chatModel = request.getChatModel();
        if (chatModel == null) {
            sendMessage(session, WebSocketMessage.error("chat.api_config_missing", "API configuration not found"), chatId);
            return;
        }
        log.info("doChat request chatId={}||username={}||request={}", chatId, username, JSONObject.toJSONString(request));
        String modelName = chatModel.getModelName();
        String openAPiLLMConfigId = chatModel.getOpenApiLLMConfig().getId();
        List<ChatModel> runningChatModelList = openApiModelScanTask.currentActiveModelList();
        ChatModel targetChatModel = runningChatModelList.stream().filter(var -> var.getModelName().equals(modelName)
                && var.getOpenApiLLMConfig().getId().equals(openAPiLLMConfigId)).findFirst().orElse(null);
        if (targetChatModel == null) {
            sendMessage(session, WebSocketMessage.error("chat.model_not_exist", String.format("Model %s does not exist, refresh the page and retry", chatModel.getModelName()), Collections.singletonMap("model", chatModel.getModelName())), chatId);
            return;
        }
        UserRoleEnum userRole = userService.getCurrentUser().getRole();
        if (targetChatModel.getVisibility() != null && !targetChatModel.getVisibility().canAccess(userRole)) {
            sendMessage(session, WebSocketMessage.error("chat.model_no_permission", "No permission to access this model"), chatId);
            return;
        }
        request.setChatModel(targetChatModel);
        chatModel = targetChatModel;
        ChatSessionConfig sessionConfig = sessionConfigManager.getConfigByRole(userService.getCurrentUser().getRole());
        if (sessionConfig != null) {
            int totalAttachments = CollectionUtils.size(request.getImageUrlList()) + CollectionUtils.size(request.getVideoUrlList())
                    + CollectionUtils.size(request.getAudioUrlList());
            if (totalAttachments > sessionConfig.getMaxAttachments()) {
                sendMessage(session, WebSocketMessage.error("chat.attachment_limit", String.format("Attachment count exceeds limit (%s)", sessionConfig.getMaxAttachments()), Collections.singletonMap("count", sessionConfig.getMaxAttachments())), chatId);
                return;
            }
            int textFileCount = CollectionUtils.size(request.getChatMediaTextList());
            if (textFileCount > sessionConfig.getMaxTextAttachments()) {
                sendMessage(session, WebSocketMessage.error("chat.text_attachment_limit", String.format("Text attachment count exceeds limit (%s)", sessionConfig.getMaxTextAttachments()), Collections.singletonMap("count", sessionConfig.getMaxTextAttachments())), chatId);
                return;
            }
            ChatSession currentSession = chatService.loadSession(chatId);
            if (currentSession != null && CollectionUtils.isNotEmpty(currentSession.getMessages())) {
                long messageCount = currentSession.getMessages().size();
                if (messageCount >= sessionConfig.getMaxMessageCount()) {
                    sendMessage(session, WebSocketMessage.error("chat.message_limit", String.format("Session message count reached the limit (%s), please start a new conversation", sessionConfig.getMaxMessageCount()), Collections.singletonMap("count", sessionConfig.getMaxMessageCount())), chatId);
                    return;
                }
            }
        }
        OpenApiLLMConfig config = chatModel.getOpenApiLLMConfig();
        RunningChatApiParamWrapper runningChatApiParamWrapper = chatIdRunningChatApiParamWrapperMap.get(chatId);
        if (runningChatApiParamWrapper == null) {
            sendMessage(session, WebSocketMessage.error("chat.connection_lost", "Connection lost, please refresh the page and retry"), chatId);
            return;
        }
        if (runningChatApiParamWrapper.getLatestAssistantMessage() != null
                || !runningChatApiParamWrapper.getGeneratingClaimed().compareAndSet(false, true)) {
            sendMessage(session, WebSocketMessage.error("chat.generating_in_progress", "The conversation is currently generating, please wait for it to finish"), chatId);
            return;
        }
        //save assistant message (提前创建，保证 try 内任意位置抛异常时 catch 仍可引用)
        final ChatMessage assistantMessage = new ChatMessage();
        try {
            ChatHistory chatHistory = chatId2ChatHistoryMap.computeIfAbsent(chatId, this::initChatHistory);
            boolean isFirstPrompt = chatHistory.getMessages().isEmpty();
            long startTime = System.currentTimeMillis();
            //save user prompt message
            ChatMessage userMessage = new ChatMessage();
            userMessage.setRole("user");
            userMessage.setContent(request.getMessage());
            userMessage.setTimestamp(startTime);
            userMessage.setImageUrlList(request.getImageUrlList());
            userMessage.setVideoUrlList(request.getVideoUrlList());
            userMessage.setAudioUrlList(request.getAudioUrlList());
            userMessage.setChatMediaTextList(request.getChatMediaTextList());
            //检查当前模型是否支持多模态输入
            List<ChatMessage> userChatMessageList = chatId2ChatMessagesMap.computeIfAbsent(chatId, k -> new ArrayList<>());
            userChatMessageList.add(userMessage);
            int checkResult = checkSupportMulti(userChatMessageList, targetChatModel);
            if (checkResult != 0) {
                String errorMsgKey;
                String errorMsg;
                switch (checkResult) {
                    case ChatModel.IMAGE_MASK:
                        errorMsgKey = "chat.model_no_image";
                        errorMsg = "Current model does not support image input";
                        break;
                    case ChatModel.VIDEO_MASK:
                        errorMsgKey = "chat.model_no_video";
                        errorMsg = "Current model does not support video input";
                        break;
                    case ChatModel.AUDIO_MASK:
                        errorMsgKey = "chat.model_no_audio";
                        errorMsg = "Current model does not support audio input";
                        break;
                    default:
                        errorMsgKey = "chat.model_no_image";
                        errorMsg = "Current model does not support image input";
                }
                log.error("failed handle chatId={} ||errorMsg={}", chatId, errorMsg);
                userChatMessageList.remove(userChatMessageList.size() - 1);
                runningChatApiParamWrapper.getGeneratingClaimed().set(false);
                sendMessage(session, WebSocketMessage.error(errorMsgKey, errorMsg), chatId);
                return;
            }
            userChatMessageList.remove(userChatMessageList.size() - 1);
            initChatStartDialog(runningChatApiParamWrapper);
            OpenApiClient
                    openApiClient = new OpenApiClient(chatModel.getModelName(), config.getBaseUrl(), chatHistory);
            openApiClient.setApiKey(config.getApiKey());
            ChatRuntimeConfig chatRuntimeConfig = Optional.ofNullable(request.getChatRuntimeConfig()).orElseGet(ChatRuntimeConfig::new);
            openApiClient.setChatRuntimeConfig(chatRuntimeConfig);
            // 避免旧页面/第三方客户端误清除会话已保存的系统提示词
            openApiClient.setSystemPrompt(chatRuntimeConfig.getSystemPrompt());
            ChatModel.ThinkConfig thinkConfig = chatModel.getThinkConfig();
            String hybridThinking = request.getHybridThinking();
            if (StringUtils.isNotBlank(hybridThinking)) {
                if (thinkConfig == null) {
                    log.warn("model {} not support thinking, but requested hybridThinking {}", modelName, hybridThinking);
                    runningChatApiParamWrapper.getGeneratingClaimed().set(false);
                    sendMessage(session, WebSocketMessage.error("chat.model.think.level.nonexists",
                            String.format("model %s does not support thinking", modelName), Collections.singletonMap("level", hybridThinking)), chatId);
                    return;
                }
                openApiClient.setChatThinkConfig(ChatModel.generateChatThinkConfig(thinkConfig, hybridThinking));
            }
            runningChatApiParamWrapper.setOpenApiClient(openApiClient);
            StringBuilder contentBuilder = new StringBuilder();
            StringBuilder thinkingBuilder = new StringBuilder();
            int[] thinkingTokens = {0};
            long[] firstTokenTime = {0};
            long[] thinkingStartTime = {0};
            long[] thinkingEndTime = {0};
            AtomicReference<OpenApiStats> openApiStatsRef = new AtomicReference<>();
            ChatStats stats = new ChatStats();
            assistantMessage.setRole("assistant");
            assistantMessage.setModelName(chatModel.getModelName());
            // live reference: partial stats (e.g. thinkingTimeMs) become visible to check_recovery while generating
            assistantMessage.setStats(stats);
            runningChatApiParamWrapper.setLatestUserMessage(userMessage);
            runningChatApiParamWrapper.setLatestAssistantMessage(assistantMessage);
            assistantMessage.setStatus(ChatMessage.MessageStatus.INIT_BEFORE);
            sendMessage(session, WebSocketMessage.streamStart(), chatId);
            userChatMessageList.add(userMessage);
            LockUtil.lockRun(this::getLock, var -> {
                chatService.addMessage(chatId, userMessage);
                assistantMessage.setStatus(ChatMessage.MessageStatus.INIT_FINISHED);
            }, chatId.hashCode());
            chatService.updateSessionModelName(chatId, chatModel.getModelName());
            if (isFirstPrompt && StringUtils.isNotBlank(request.getMessage())) {
                // 仅当标题仍为默认值时才自动生成, 避免覆盖用户手动重命名的标题
                ChatSession sessionForTitle = chatService.loadSession(chatId);
                String currentTitle = sessionForTitle == null ? null : sessionForTitle.getTitle();
                if (StringUtils.isBlank(currentTitle) || SystemConstants.DEFAULT_SESSION_TITLE_KEY.equals(currentTitle)) {
                    String newTitle = request.getMessage();
                    if (newTitle.length() > 10) {
                        newTitle = newTitle.substring(0, 10);
                    }
                    chatService.updateSessionTitle(chatId, newTitle);
                    sendMessage(Optional.ofNullable(runningChatApiParamWrapper.getActiveSocketSession()).orElse(session),
                            WebSocketMessage.titleUpdate(newTitle), chatId);
                }
            }
            runningChatApiParamWrapper.setLatestContent(contentBuilder);
            runningChatApiParamWrapper.setLatestThinkingContent(thinkingBuilder);
            AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<>();
            BiConsumer<Throwable, String> biChunkConsumer = (th, chunk) -> {
                runningChatApiParamWrapper.setLastActiveTime(System.currentTimeMillis());
                if (th != null) {
                    throwableAtomicReference.set(th);
                    return;
                }
                ReentrantLock lock = getLock(chatId.hashCode());
                try {
                    lock.lock();
                    if (runningChatApiParamWrapper.getStopFlag().get()) {
                        return;
                    }
                    runningChatApiParamWrapper.setLastActiveTime(System.currentTimeMillis());
                    assistantMessage.setStatus(ChatMessage.MessageStatus.GENERATION);
                    contentBuilder.append(chunk);
                    if (firstTokenTime[0] == 0) {
                        firstTokenTime[0] = System.currentTimeMillis();
                        thinkingEndTime[0] = System.currentTimeMillis();
                        if (thinkingStartTime[0] > 0 && thinkingEndTime[0] > 0) {
                            stats.setThinkingTimeMs(thinkingEndTime[0] - thinkingStartTime[0]);
                        }
                    }
                    WebSocketMessage contentWebSocketMessage = WebSocketMessage.contentChunk(chunk);
                    contentWebSocketMessage.setThinkingTimeMs(stats.getThinkingTimeMs());
                    OpenApiStats openApiStats = openApiStatsRef.get();
                    if (openApiStats != null && openApiStats.getUsage() != null) {
                        contentWebSocketMessage.setTotalTokens(openApiStats.getUsage().getTotalTokens());
                    }
                    sendMessage(Optional.ofNullable(runningChatApiParamWrapper.getActiveSocketSession()).orElse(session),
                            contentWebSocketMessage, chatId);
                } finally {
                    lock.unlock();
                }
            };

            BiConsumer<Throwable, OpenApiStats> openApiStatsBiConsumer = (th, stat) -> {
                if (stat != null) {
                    openApiStatsRef.set(stat);
                }
            };

            BiConsumer<Throwable, String> biThinkingConsumer = (th, thinking) -> {
                runningChatApiParamWrapper.setLastActiveTime(System.currentTimeMillis());
                if (th != null) {
                    throwableAtomicReference.set(th);
                    return;
                }
                ReentrantLock lock = getLock(chatId.hashCode());
                try {
                    lock.lock();
                    if (runningChatApiParamWrapper.getStopFlag().get()) {
                        return;
                    }
                    runningChatApiParamWrapper.setLastActiveTime(System.currentTimeMillis());
                    assistantMessage.setStatus(ChatMessage.MessageStatus.THINKING);
                    thinkingBuilder.append(thinking);
                    thinkingTokens[0]++;
                    stats.setThinkingTokens(thinkingTokens[0]);
                    if (thinkingStartTime[0] == 0) {
                        thinkingStartTime[0] = System.currentTimeMillis();
                    } else {
                        stats.setThinkingTimeMs(System.currentTimeMillis() - thinkingStartTime[0]);
                    }
                    WebSocketMessage thinkingWebSocketMessage = WebSocketMessage.thinkingChunk(thinking);
                    // 实时下发当前思考耗时，前端据此实时刷新
                    thinkingWebSocketMessage.setThinkingTimeMs(stats.getThinkingTimeMs());
                    OpenApiStats thinkingOpenApiStats = openApiStatsRef.get();
                    if (thinkingOpenApiStats != null && thinkingOpenApiStats.getUsage() != null) {
                        thinkingWebSocketMessage.setTotalTokens(thinkingOpenApiStats.getUsage().getTotalTokens());
                    }
                    thinkingEndTime[0] = System.currentTimeMillis();
                    sendMessage(Optional.ofNullable(runningChatApiParamWrapper.getActiveSocketSession()).orElse(session),
                            thinkingWebSocketMessage, chatId);
                } finally {
                    lock.unlock();
                }
            };

            openApiClient.sendStreamRequestWithMessage(userMessage, biChunkConsumer, biThinkingConsumer, openApiStatsBiConsumer);
            if (throwableAtomicReference.get() != null) {
                throw new RuntimeException(throwableAtomicReference.get());
            }
            long endTime = System.currentTimeMillis();
            boolean isStopped = runningChatApiParamWrapper.getStopFlag().get();
            String response = contentBuilder.toString();

            assistantMessage.setContent(response);
            assistantMessage.setThinkingContent(thinkingBuilder.toString());
            assistantMessage.setTimestamp(endTime);
            LockUtil.lockRun(this::getLock, var -> assistantMessage.setStatus(isStopped ? ChatMessage.MessageStatus.STOP : ChatMessage.MessageStatus.FINISH), chatId.hashCode());
            // Populate stats from OpenApiStats
            OpenApiStats openApiStats = openApiStatsRef.get();
            if (openApiStats != null) {
                ChatStats apiStats = OpenApiStats.convertChatStats(openApiStats);
                OpenApiStats.Timings openapiTimings = openApiStats.getTimings();
                OrikaBeanConverter.mergeKeepNonNull(stats, apiStats);
                if (Optional.ofNullable(openapiTimings).map(OpenApiStats.Timings::getNCtx).orElse(null) == null) {
                    stats.setNCtx(targetChatModel.getContentLength());
                }
            }

            // Keep original logic for thinkingTokens, thinkingTimeMs, firstTokenLatencyMs
            stats.setThinkingTokens(thinkingTokens[0]);
            stats.setProcessingTimeMs(endTime - startTime);
            if (thinkingStartTime[0] > 0) {
                stats.setFirstTokenLatencyMs(thinkingStartTime[0] - startTime);
            } else if (firstTokenTime[0] > 0) {
                stats.setFirstTokenLatencyMs(firstTokenTime[0] - startTime);
            }
            chatService.addMessage(chatId, assistantMessage);

            WebSocketMessage lastWebSocketMessage = WebSocketMessage.streamEnd(assistantMessage);
            log.info("stream end ||chatId={}", chatId);
            sendMessage(Optional.ofNullable(runningChatApiParamWrapper.getActiveSocketSession()).orElse(session),
                    lastWebSocketMessage, chatId);
        } catch (Exception e) {
            log.error("流式聊天请求失败 chatId={}", chatId, e);
            String requestFailedDetail = ExceptionUtil.traceRealException(e).getMessage();
            WebSocketMessage lastWebSocketMessage = WebSocketMessage.error("chat.request_failed", "Chat request failed: " + requestFailedDetail, Collections.singletonMap("detail", requestFailedDetail));
            LockUtil.lockRun(this::getLock, var -> assistantMessage.setStatus(ChatMessage.MessageStatus.ERROR), chatId.hashCode());
            lastWebSocketMessage.setLastAssistantMessage(assistantMessage);
            sendMessage(Optional.ofNullable(runningChatApiParamWrapper.getActiveSocketSession()).orElse(session),
                    lastWebSocketMessage, chatId);
        } finally {
            runningChatApiParamWrapper.setLatestAssistantMessage(null);
            runningChatApiParamWrapper.setLatestUserMessage(null);
            runningChatApiParamWrapper.getGeneratingClaimed().set(false);
            runningChatApiParamWrapper.getStopFlag().set(true);
            WebSocketSession activeSocketSession = runningChatApiParamWrapper.getActiveSocketSession();
            if (activeSocketSession == null || !activeSocketSession.isOpen()) {
                removeChatContext(chatId);
            }
        }
    }

    private void sendMessage(WebSocketSession session, WebSocketMessage message, String chatId) {
        if (session == null) {
            return;
        }
        Object sendLock = getSendLock(session);
        synchronized (sendLock) {
            try {
                if (session.isOpen()) {
                    message.setChatId(chatId);
                    session.sendMessage(new TextMessage(JSONObject.toJSONString(message)));
                }
            } catch (IOException e) {
                log.error("发送消息失败 chatId={}", chatId, e);
            }
        }
    }

    private void handleChatRequest(WebSocketSession session, JSONObject payload) {
        ChatRequest request = payload.toJavaObject(ChatRequest.class);
        String sessionId = payload.getString("sessionId");
        if (!injectCurrentLoginUser(session, sessionId)) {
            log.warn("session invalid or expired||request={}", payload.toJSONString());
            return;
        }
        Map<String, Object> map = ThreadLocalContext.getAll();
        chatTaskExecutor.execute(() -> {
            try {
                ThreadLocalContext.putAll(map);
                doChat(session, request);
            } catch (Exception e) {
                log.error("Chat request error", e);
                sendError(session, "chat.request_failed", "Chat request failed: " + e.getMessage(), Collections.singletonMap("detail", e.getMessage()), request.getChatId());
            } finally {
                ThreadLocalContext.remove(TraceConstants.WEB_SOCKET_CURRENT_USER_INFO_KEY);
            }
        });

    }

    private void handleStopGeneration(WebSocketSession session, JSONObject payload) {
        String chatId = payload.getString("chatId");
        if (StringUtils.isNotBlank(chatId)) {
            log.info("停止chatId={}", chatId);
            RunningChatApiParamWrapper runningChatApiParamWrapper = chatIdRunningChatApiParamWrapperMap.get(chatId);
            if (runningChatApiParamWrapper == null) {
                return;
            }
            AtomicBoolean stopFlag = runningChatApiParamWrapper.getStopFlag();
            if (stopFlag != null) {
                stopFlag.set(true);
            }
            OpenApiClient chatApiClient = runningChatApiParamWrapper.getOpenApiClient();
            if (chatApiClient != null) {
                chatApiClient.cancelCurrentCall();
            }
        }
    }


    private void handleCheckRecovery(WebSocketSession session, JSONObject payload) {
        String chatId = payload.getString("chatId");
        if (StringUtils.isBlank(chatId)) {
            log.warn("chatId is blank ,no recovery operation");
            return;
        }
        ReentrantLock lock = getLock(chatId.hashCode());
        try {
            lock.lock();
            RunningChatApiParamWrapper runningChatApiParamWrapper = chatIdRunningChatApiParamWrapperMap.get(chatId);
            if (runningChatApiParamWrapper == null) {
                return;
            }
            ChatMessage latestAssistantMessage = runningChatApiParamWrapper.getLatestAssistantMessage();
            ChatMessage latestUserMessage = runningChatApiParamWrapper.getLatestUserMessage();
            if (latestAssistantMessage != null && ChatMessage.MessageStatus.isInRunning(latestAssistantMessage.getStatus())) {
                log.info("check recovery chatId={}", chatId);
                StringBuilder thinkingBuilder = runningChatApiParamWrapper.getLatestThinkingContent();
                StringBuilder contentBuilder = runningChatApiParamWrapper.getLatestContent();
                if (thinkingBuilder != null) {
                    latestAssistantMessage.setThinkingContent(thinkingBuilder.toString());
                }
                if (contentBuilder != null) {
                    latestAssistantMessage.setContent(contentBuilder.toString());
                }
                sendMessage(session, WebSocketMessage.sessionRecovery(latestAssistantMessage,
                        latestAssistantMessage.getStatus() == ChatMessage.MessageStatus.INIT_BEFORE ? latestUserMessage : null), chatId);
            }
        } finally {
            lock.unlock();
        }
    }

    private void sendError(WebSocketSession session, String msgKey, String message, String chatId) {
        sendMessage(session, WebSocketMessage.error(msgKey, message), chatId);
    }

    private void sendError(WebSocketSession session, String msgKey, String message, Map<String, Object> params, String chatId) {
        sendMessage(session, WebSocketMessage.error(msgKey, message, params), chatId);
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        String chatId = extractChatId(session);
        if (StringUtils.isBlank(chatId)) {
            removeSendLock(session);
            return;
        }
        RunningChatApiParamWrapper wrapper = chatIdRunningChatApiParamWrapperMap.get(chatId);
        if (wrapper == null || !session.equals(wrapper.getActiveSocketSession())) {
            removeSendLock(session);
            return;
        }
        if (wrapper.getHeartbeatFuture() != null) {
            wrapper.getHeartbeatFuture().cancel(false);
        }
        if (wrapper.getLatestAssistantMessage() == null
                || !ChatMessage.MessageStatus.isInRunning(wrapper.getLatestAssistantMessage().getStatus())) {
            removeChatContext(chatId);
        } else {
            log.info("Keep running chat context for recovery after close chatId={}, status={}", chatId, status);
        }
        removeSendLock(session);
    }

    public List<String> getActiveChatIdsByUsername(String username) {
        return chatIdRunningChatApiParamWrapperMap.entrySet().stream()
                .filter(e -> {
                    String wrapperSessionId = e.getValue().getSessionId();
                    if (wrapperSessionId == null) {
                        return false;
                    }
                    String wrapperUsername = loginService.getCurrentUsername(wrapperSessionId);
                    if (!username.equals(wrapperUsername)) {
                        return false;
                    }
                    ChatMessage msg = e.getValue().getLatestAssistantMessage();
                    return msg != null && ChatMessage.MessageStatus.isInRunning(msg.getStatus());
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
