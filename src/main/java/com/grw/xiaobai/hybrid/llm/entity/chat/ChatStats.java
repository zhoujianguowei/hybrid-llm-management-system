package com.grw.xiaobai.hybrid.llm.entity.chat;

import java.io.Serializable;
import lombok.Data;

@Data
public class ChatStats implements Serializable {
    /**
     * 首 token 延迟（ms）
     */
    private Long firstTokenLatencyMs;
    /**
     * 思考阶段 token 数
     */
    private Integer thinkingTokens;
    /**
     * 思考耗时（ms）
     */
    private Long thinkingTimeMs;
    /**
     * 生成 token 数
     */
    private Integer completionTokens;
    /**
     * 提示词 token 数
     */
    private Integer promptTokens;
    /**
     * 总 token 数
     */
    private Integer totalTokens;
    /**
     * 缓存 token 数
     */
    private Integer cachedTokens;
    /**
     * 提示词处理耗时（ms）
     */
    private Double promptMs;
    /**
     * 提示词处理速度（token/s）
     */
    private Double promptPerSecond;
    /**
     * 生成耗时（ms）
     */
    private Double predictedMs;
    /**
     * 生成速度（token/s）
     */
    private Double predictedPerSecond;
    /**
     * 上下文窗口大小
     */
    private Integer nCtx;
    /**
     * 总处理耗时（ms）
     */
    private Long processingTimeMs;
}
