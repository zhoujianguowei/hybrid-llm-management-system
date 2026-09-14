package com.grw.xiaobai.hybrid.llm.entity.chat;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ChatSession implements Serializable {
    private String chatId;
    private String title;
    private Long createdAt;
    private Long updatedAt;
    private String apiConfigId;
    private String apiConfigName;
    private String modelName;
    private List<ChatMessage> messages;
    private ChatStats stats;
    private boolean generating;
    private String generatingContent;
    private String generatingThinking;
    private Long generatingStartTime;
    private Integer type;
    private String thinkingMode;
    private ChatRuntimeConfig chatRuntimeConfig;
}