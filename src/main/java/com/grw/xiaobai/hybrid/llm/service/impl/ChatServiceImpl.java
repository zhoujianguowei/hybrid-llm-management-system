package com.grw.xiaobai.hybrid.llm.service.impl;

import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.utils.converter.OrikaBeanConverter;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatMediaText;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatMessage;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatModel;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSession;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSessionSummary;
import com.grw.xiaobai.hybrid.llm.entity.chat.ModelFuncConfig;
import com.grw.xiaobai.hybrid.llm.request.NewSessionRequest;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiLLMConfig;
import com.grw.xiaobai.hybrid.llm.response.SessionListResponse;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.user.UserInfo;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatRuntimeConfig;
import com.grw.xiaobai.hybrid.llm.request.CopyUserMessageRequest;
import com.grw.xiaobai.hybrid.llm.entity.user.UserContext;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import com.grw.xiaobai.hybrid.llm.exception.BusinessLogicException;
import com.grw.xiaobai.hybrid.llm.manager.ModelFuncConfigManager;
import com.grw.xiaobai.hybrid.llm.manager.OpenApiLLMConfigManager;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatHistory;
import com.grw.xiaobai.hybrid.llm.service.ChatService;
import com.grw.xiaobai.hybrid.llm.service.ChatSessionService;
import com.grw.xiaobai.hybrid.llm.service.UserService;
import com.grw.xiaobai.hybrid.llm.task.OpenApiModelScanTask;

import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Resource
    private OpenApiLLMConfigManager openApiLLMConfigManager;

    @Resource
    private ModelFuncConfigManager modelFuncConfigManager;

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private OpenApiModelScanTask openApiModelScanTask;

    @Resource
    private UserService userService;

    public List<OpenApiLLMConfig> getApiConfigs() {
        UserRoleEnum userRole = userService.getCurrentUser().getRole();
        if (UserRoleEnum.admin.equals(userRole)) {
            return openApiLLMConfigManager.getConfigs();
        }
        // 非管理员不暴露 baseUrl / apiKey，仅返回配置 ID 等基础信息
        List<OpenApiLLMConfig> configs = OrikaBeanConverter.convertList(openApiLLMConfigManager.getConfigs(), OpenApiLLMConfig.class);
        for (OpenApiLLMConfig config : configs) {
            config.setBaseUrl(null);
            config.setApiKey(null);
        }
        return configs;
    }

    public ResultModel<OpenApiLLMConfig> saveApiConfig(OpenApiLLMConfig config) {
        config.validate();
        boolean isEdit = StringUtils.isNotBlank(config.getId());
        boolean exists = isEdit
                ? openApiLLMConfigManager.baseUrlExists(config.getId(), config.getBaseUrl())
                : openApiLLMConfigManager.baseUrlExists(config.getBaseUrl());
        if (exists) {
            return ResultModel.fail("chat.openapi_exists", "OpenAPI endpoint already exists");
        }
        OpenApiLLMConfig saved = openApiLLMConfigManager.saveConfig(config);
        return ResultModel.OK(saved);
    }

    public Boolean deleteApiConfig(String configId) {
        return openApiLLMConfigManager.deleteConfig(configId);
    }

    public List<ModelFuncConfig> getModelFuncConfigs() {
        return modelFuncConfigManager.getConfigs();
    }

    public ResultModel<ModelFuncConfig> saveModelFuncConfig(ModelFuncConfig config) {
        return modelFuncConfigManager.saveConfig(config);
    }

    public Boolean deleteModelFuncConfig(String configId) {
        return modelFuncConfigManager.deleteConfig(configId);
    }

    public Boolean moveModelFuncOrder(String configId, boolean up) {
        return modelFuncConfigManager.moveOrder(configId, up);
    }

    private List<ChatModel> getModelList() {
        return openApiModelScanTask.currentActiveModelList();
    }

    public SessionListResponse getSessionList(int offset, int limit) {
        String username = userService.getCurrentUser().getUsername();
        return buildSessionListResponse(username, offset, limit, 0);
    }

    public ChatSession createSession(NewSessionRequest request) {
        String username = userService.getCurrentUser().getUsername();
        ChatSession session = chatSessionService.createSession(username, request.getApiConfigId(), request.getApiConfigName(), request.getModelName());
        UserContext userContext = userService.getCurrentUser().getUserContext();
        userContext.getChatContext().setLastOpenedChatId(session.getChatId());
        return session;
    }

    public ChatSession loadSession(String chatSessionId) {
        String username = userService.getCurrentUser().getUsername();
        return chatSessionService.loadSession(username, chatSessionId);
    }

    public Boolean deleteSession(String chatSessionId) {
        UserInfo currentUser = userService.getCurrentUser();
        UserContext.ChatContext chatContext = currentUser.getUserContext().getChatContext();
        if (StringUtils.isNotBlank(chatContext.getLastOpenedChatId()) && chatContext.getLastOpenedChatId().equals(chatSessionId) ||
                Optional.ofNullable(chatContext.getActiveChatIdList()).orElse(Lists.newArrayList()).contains(chatSessionId)) {
            log.warn("can't delete lastOpenChat session or ongoing chat session");
            throw BusinessLogicException.builder().msgKey("chat.delete_active_session").msg("Cannot delete currently open or active session").build();
        }
        String username = currentUser.getUsername();
        return chatSessionService.deleteSession(username, chatSessionId);
    }

    public void addMessage(String chatSessionId, ChatMessage message) {
        String username = userService.getCurrentUser().getUsername();
        chatSessionService.addMessage(username, chatSessionId, message);
    }

    public void updateSessionTitle(String chatSessionId, String title) {
        String username = userService.getCurrentUser().getUsername();
        ChatSession update = new ChatSession();
        update.setTitle(title);
        chatSessionService.updateSession(username, chatSessionId, update);
    }

    public void updateSessionModelName(String chatSessionId, String modelName) {
        String username = userService.getCurrentUser().getUsername();
        ChatSession update = new ChatSession();
        update.setModelName(modelName);
        chatSessionService.updateSession(username, chatSessionId, update);
    }

    public void updateSessionThinkingMode(String chatSessionId, String thinkingMode) {
        String username = userService.getCurrentUser().getUsername();
        ChatSession update = new ChatSession();
        update.setThinkingMode(thinkingMode);
        chatSessionService.updateSession(username, chatSessionId, update);
    }

    public void updateSessionRuntimeConfig(String chatSessionId, ChatRuntimeConfig runtimeConfig) {
        if (runtimeConfig == null) {
            return;
        }
        String username = userService.getCurrentUser().getUsername();
        // 前端始终携带完整配置（未设置字段为 null），此处整体替换即可，null 表示清除该字段
        ChatSession update = new ChatSession();
        update.setChatRuntimeConfig(runtimeConfig);
        chatSessionService.updateSession(username, chatSessionId, update);
    }

    public ChatSession getChatSession(String username, String chatSessionId) {
        return chatSessionService.loadSession(username, chatSessionId);
    }


    public int cleanupExpiredChatSession() {
        return chatSessionService.cleanupExpiredChatSessions();
    }


    public List<ChatModel> getAccessibleModelList() {
        List<ChatModel> modelList = getModelList();
        UserRoleEnum userRole = userService.getCurrentUser().getRole();
        modelList = modelList.stream()
                .filter(m -> m.getVisibility() != null && m.getVisibility().canAccess(userRole))
                .collect(java.util.stream.Collectors.toList());
        if (!UserRoleEnum.admin.equals(userRole)) {
            modelList = OrikaBeanConverter.convertList(modelList, ChatModel::deepClone);

            for (ChatModel model : modelList) {
                OpenApiLLMConfig config = model.getOpenApiLLMConfig();
                if (config != null) {
                    config.setBaseUrl(null);
                    config.setApiKey(null);
                    model.setOpenApiLLMConfig(config);
                }
            }
        }
        return modelList;
    }

    public void updateSessionType(String chatSessionId, Integer type) {
        String username = userService.getCurrentUser().getUsername();
        ChatSession update = new ChatSession();
        update.setType(type);
        chatSessionService.updateSession(username, chatSessionId, update);
    }

    public SessionListResponse getPinnedSessions(int offset, int limit) {
        String username = userService.getCurrentUser().getUsername();
        return buildSessionListResponse(username, offset, limit, 1);
    }

    private SessionListResponse buildSessionListResponse(String username, int offset, int limit, Integer type) {
        int[] totalCount = new int[1];
        List<ChatSessionSummary> sessions = chatSessionService.getSessionList(username, offset, limit, type, totalCount);
        sessions.forEach(var -> var.setMessages(null));
        SessionListResponse response = new SessionListResponse();
        response.setSessions(sessions);
        response.setTotal(totalCount[0]);
        response.setHasMore(offset + limit < totalCount[0]);
        return response;
    }

    public String copyUserMessageText(CopyUserMessageRequest request) {
        StringBuilder sb = new StringBuilder();
        String content = Optional.ofNullable(request.getContent()).orElse("");
        sb.append(content);

        if (CollectionUtils.isNotEmpty(request.getChatMediaTextList())) {
            sb.append("\n 文件: ").append(buildFileNameLabel(request.getChatMediaTextList()));
            String textDocXml = ChatHistory.buildTextDocumentXml(request.getChatMediaTextList());
            if (StringUtils.isNotBlank(textDocXml)) {
                sb.append(textDocXml);
            }
        }
        return sb.toString();
    }

    private String buildFileNameLabel(List<ChatMediaText> chatMediaTextList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chatMediaTextList.size(); i++) {
            ChatMediaText item = chatMediaTextList.get(i);
            if (i > 0) sb.append(", ");
            sb.append(item.getName());
            if (StringUtils.isNotBlank(item.getRelativePath())) {
                sb.append(" (").append(item.getRelativePath()).append(")");
            }
        }
        return sb.toString();
    }
}