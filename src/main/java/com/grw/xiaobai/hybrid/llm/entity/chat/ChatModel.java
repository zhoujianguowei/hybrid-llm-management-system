package com.grw.xiaobai.hybrid.llm.entity.chat;

import com.alibaba.fastjson.JSON;
import com.grw.xiaobai.hybrid.llm.constant.ThinkingConstants;

import com.grw.xiaobai.hybrid.llm.enums.ModelTypeEnum;
import com.grw.xiaobai.hybrid.llm.enums.ModelVisibilityEnum;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ChatModel implements Cloneable {
    private OpenApiLLMConfig openApiLLMConfig;
    private String modelName;
    private int contentLength;
    private int multi;
    public static final int IMAGE_MASK = 0x01;
    public static final int VIDEO_MASK = 0x02;
    public static final int AUDIO_MASK = 0x04;
    public static final int THINKING_MASK = 0x08;
    private boolean autoDetect;
    private ModelTypeEnum modelTypeEnum;
    private ModelVisibilityEnum visibility = ModelVisibilityEnum.PUBLIC;
    private ThinkConfig thinkConfig;
    public static ChatThinkConfig generateChatThinkConfig(ThinkConfig thinkConfig, String hybridThinking) {
        ChatThinkConfig chatThinkConfig = new ChatThinkConfig();
        if (ThinkingConstants.OFF_VALUE.equals(hybridThinking)) {
            chatThinkConfig.setEnableThinking(false);
            chatThinkConfig.setEnableThinkingParamName(thinkConfig.getEnableThinkingParamName());
        } else if (ThinkingConstants.ON_VALUE.equals(hybridThinking)) {
            chatThinkConfig.setEnableThinking(true);
            chatThinkConfig.setEnableThinkingParamName(thinkConfig.getEnableThinkingParamName());
        } else {
            if (StringUtils.isNotBlank(thinkConfig.getParamName()) && thinkConfig.getThinkingLevel() != null
                    && thinkConfig.getThinkingLevel().contains(hybridThinking)) {
                chatThinkConfig.setParamName(thinkConfig.getParamName());
                chatThinkConfig.setThinkingLevel(hybridThinking);
                if (StringUtils.isNotBlank(thinkConfig.getEnableThinkingParamName())) {
                    chatThinkConfig.setEnableThinking(!ThinkingConstants.NO_THINK.equals(hybridThinking));
                    chatThinkConfig.setEnableThinkingParamName(thinkConfig.getEnableThinkingParamName());
                }
            }
        }
        return chatThinkConfig;
    }

    @Data
    public static class ThinkConfig {
        private String paramName;
        private List<String> thinkingLevel;
        private Boolean enableThinking;
        private String enableThinkingParamName;

        public String toChatTemplateKwargs(String rawLevel) {
            ChatThinkConfig chatThinkConfig = generateChatThinkConfig(this, rawLevel);
            Map<String, Object> paramMap = new LinkedHashMap<>();
            if (StringUtils.isNotBlank(chatThinkConfig.getEnableThinkingParamName())) {
                paramMap.put(chatThinkConfig.getEnableThinkingParamName(), chatThinkConfig.getEnableThinking());
            }
            if (StringUtils.isNotBlank(chatThinkConfig.getParamName())) {
                paramMap.put(chatThinkConfig.getParamName(), chatThinkConfig.getThinkingLevel());
            }
            if (paramMap.isEmpty()) {
                return null;
            }
            return JSON.toJSONString(paramMap);
        }
    }

    public boolean supportImage() {
        return (multi & IMAGE_MASK) > 0;
    }

    public boolean supportVideo() {
        return (multi & VIDEO_MASK) > 0;
    }

    public boolean supportAudio() {
        return (multi & AUDIO_MASK) > 0;
    }

    public void enableImage() {
        multi |= IMAGE_MASK;
    }

    public boolean supportThinking() {
        return thinkConfig != null;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        ChatModel cloneChatModel = (ChatModel) super.clone();
        if (this.getOpenApiLLMConfig() != null) {
            cloneChatModel.setOpenApiLLMConfig((OpenApiLLMConfig) this.getOpenApiLLMConfig().clone());
        }
        return cloneChatModel;
    }

    public static ChatModel deepClone(ChatModel chatModel) {
        try {
            return (ChatModel) chatModel.clone();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
