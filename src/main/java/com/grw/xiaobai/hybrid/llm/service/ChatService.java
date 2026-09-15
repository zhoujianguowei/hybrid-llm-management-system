package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.entity.chat.ChatMessage;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatModel;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSession;
import com.grw.xiaobai.hybrid.llm.entity.chat.ModelFuncConfig;
import com.grw.xiaobai.hybrid.llm.request.NewSessionRequest;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiLLMConfig;
import com.grw.xiaobai.hybrid.llm.response.SessionListResponse;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatRuntimeConfig;
import com.grw.xiaobai.hybrid.llm.request.CopyUserMessageRequest;

import java.util.List;

public interface ChatService {
    List<OpenApiLLMConfig> getApiConfigs();

    ResultModel<OpenApiLLMConfig> saveApiConfig(OpenApiLLMConfig config);

    Boolean deleteApiConfig(String configId);

    List<ModelFuncConfig> getModelFuncConfigs();

    ResultModel<ModelFuncConfig> saveModelFuncConfig(ModelFuncConfig config);

    Boolean deleteModelFuncConfig(String configId);

    Boolean moveModelFuncOrder(String configId, boolean up);

    SessionListResponse getSessionList(int offset, int limit);

    ChatSession createSession(NewSessionRequest request);

    ChatSession loadSession(String chatSessionId);

    Boolean deleteSession(String chatSessionId);

    void addMessage(String chatSessionId, ChatMessage message);

    void updateSessionTitle(String chatSessionId, String title);

    void updateSessionModelName(String chatSessionId, String modelName);

    void updateSessionThinkingMode(String chatSessionId, String thinkingMode);

    void updateSessionRuntimeConfig(String chatSessionId, ChatRuntimeConfig runtimeConfig);

    ChatSession getChatSession(String username, String chatSessionId);

    int cleanupExpiredChatSession();

    List<ChatModel> getAccessibleModelList();

    void updateSessionType(String chatSessionId, Integer type);

    SessionListResponse getPinnedSessions(int offset, int limit);

    String copyUserMessageText(CopyUserMessageRequest request);
}
