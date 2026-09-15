package com.grw.xiaobai.hybrid.llm.entity.chat;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ChatSessionSummary implements Serializable {
    private String chatId;
    private String title;
    private Long createdAt;
    private Long updatedAt;
    private String apiConfigName;
    private String lastMessage;
    private Integer messageCount;
    private List<ChatMessage> messages;
    private Integer type;
}