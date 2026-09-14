package com.grw.xiaobai.hybrid.llm.entity.chat;

import lombok.Data;

@Data
public class ChatThinkConfig {
    private String paramName;
    private String thinkingLevel;
    private Boolean enableThinking;
    private String enableThinkingParamName;
}
