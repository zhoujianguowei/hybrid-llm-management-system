package com.grw.xiaobai.hybrid.llm.entity.chat;

import lombok.Data;

/**
 * 会话级运行时配置。
 * 采样参数使用包装类型：null 表示未设置，请求 payload 中不携带该字段（由服务端使用自身默认值）。
 */
@Data
public class ChatRuntimeConfig {
    private boolean attachReasoningContent;
    private String systemPrompt;
    private Double temperature;
    private Double topP;
    /** 0 或 null 表示未设置：请求 payload 中不携带 max_tokens（由服务端使用自身默认值） */
    private Integer maxTokens;
    private Double presencePenalty;
    private Double frequencyPenalty;
}
