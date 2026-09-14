package com.grw.xiaobai.hybrid.llm.entity.chat;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class OpenApiStats {

    @JSONField(name = "usage")
    private Usage usage;

    @JSONField(name = "timings")
    private Timings timings;

    @Data
    public static class Usage {

        @JSONField(name = "completion_tokens")
        private Integer completionTokens;

        @JSONField(name = "prompt_tokens")
        private Integer promptTokens;

        @JSONField(name = "total_tokens")
        private Integer totalTokens;

        @JSONField(name = "prompt_tokens_details")
        private PromptTokensDetails promptTokensDetails;

        @Data
        public static class PromptTokensDetails {

            @JSONField(name = "cached_tokens")
            private Integer cachedTokens;
        }
    }

    @Data
    public static class Timings {

        @JSONField(name = "prompt_n")
        private Integer promptN;

        @JSONField(name = "prompt_ms")
        private Double promptMs;

        @JSONField(name = "prompt_per_token_ms")
        private Double promptPerTokenMs;

        @JSONField(name = "prompt_per_second")
        private Double promptPerSecond;

        @JSONField(name = "predicted_n")
        private Integer predictedN;

        @JSONField(name = "predicted_ms")
        private Double predictedMs;

        @JSONField(name = "predicted_per_token_ms")
        private Double predictedPerTokenMs;

        @JSONField(name = "predicted_per_second")
        private Double predictedPerSecond;

        @JSONField(name = "n_ctx")
        private Integer nCtx;
    }


    public static ChatStats convertChatStats(OpenApiStats openApiStats) {
        if (openApiStats == null) {
            return new ChatStats();
        }
        ChatStats stats = new ChatStats();
        Usage usage = openApiStats.getUsage();
        if (usage != null) {
            stats.setCompletionTokens(usage.getCompletionTokens());
            stats.setPromptTokens(usage.getPromptTokens());
            stats.setTotalTokens(usage.getTotalTokens());
            if (usage.getPromptTokensDetails() != null) {
                stats.setCachedTokens(usage.getPromptTokensDetails().getCachedTokens());
            }
        }
        Timings timings = openApiStats.getTimings();
        if (timings != null) {
            stats.setPromptMs(timings.getPromptMs());
            stats.setPromptPerTokenMs(timings.getPromptPerTokenMs());
            stats.setPromptPerSecond(timings.getPromptPerSecond());
            stats.setPredictedMs(timings.getPredictedMs());
            stats.setPredictedPerTokenMs(timings.getPredictedPerTokenMs());
            stats.setPredictedPerSecond(timings.getPredictedPerSecond());
            stats.setNCtx(timings.getNCtx());
        }
        return stats;
    }
}
