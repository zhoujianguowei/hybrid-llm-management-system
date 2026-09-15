package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.entity.chat.ChatModel;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiLLMConfig;

import java.util.List;

public interface OpenApiModelService {
    List<ChatModel> scanRunningModelList(OpenApiLLMConfig openApiLLMConfig, StringBuilder errorMsgBuilder);

    List<ChatModel> scanRunningModelList(OpenApiLLMConfig openApiLLMConfig);

    ChatModel enrichWithFuncConfig(ChatModel chatModel);
}
