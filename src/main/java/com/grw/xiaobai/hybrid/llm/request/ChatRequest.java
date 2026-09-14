package com.grw.xiaobai.hybrid.llm.request;

import java.io.Serializable;
import java.util.List;

import com.grw.xiaobai.hybrid.llm.entity.chat.ChatModel;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatMediaText;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatRuntimeConfig;
import lombok.Data;

@Data
public class ChatRequest implements Serializable {
    private String apiConfigId;
    private String message;
    private String chatId;
    private ChatModel chatModel;
    private boolean formatJson;
    private String hybridThinking;
    private ChatRuntimeConfig chatRuntimeConfig;
    private List<String> imageUrlList;
    private List<String> videoUrlList;
    private List<String> audioUrlList;
    private List<ChatMediaText> chatMediaTextList;
}