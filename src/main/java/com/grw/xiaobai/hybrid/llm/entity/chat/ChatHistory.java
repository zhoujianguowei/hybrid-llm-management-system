package com.grw.xiaobai.hybrid.llm.entity.chat;

import cn.hutool.core.lang.Pair;
import com.alibaba.fastjson.JSONObject;
import com.grw.xiaobai.hybrid.llm.constant.ThreadPoolConstants;
import com.grw.xiaobai.hybrid.llm.utils.image.MediaCacheHandler;
import com.grw.xiaobai.hybrid.llm.service.SpringContextUtil;
import com.grw.xiaobai.hybrid.llm.utils.CompletableFutureUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 管理与 OpenAI 兼容 API 的对话历史。
 * 这是一个客户端组件，用于模拟有状态的对话。
 */
@Data
public class ChatHistory {

    private final List<Map<String, Object>> messages;
    private final int limit;

    public ChatHistory(int limit) {
        this.messages = new ArrayList<>();
        this.limit = limit;
    }

    public ChatHistory() {
        this(Integer.MAX_VALUE);
    }

    /**
     * （可选）为对话设置一个系统级的指令。
     * 若历史首条已是 system 消息则覆盖其内容，否则在头部插入；
     * 传入空白值表示清除：移除历史头部的 system 消息（若有）。
     *
     * @param systemPrompt 例如 "你是一个乐于助人的AI助手。"
     */
    public void setSystemPrompt(String systemPrompt) {
        if (StringUtils.isBlank(systemPrompt)) {
            if (!this.messages.isEmpty() && "system".equals(this.messages.get(0).get("role"))) {
                this.messages.remove(0);
            }
            return;
        }
        if (!this.messages.isEmpty() && "system".equals(this.messages.get(0).get("role"))) {
            this.messages.get(0).put("content", systemPrompt);
            return;
        }
        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        this.messages.add(0, systemMessage);
    }

    /**
     * 添加用户的发言（纯文本）。
     *
     * @param chatMessage 用户发言消息。
     */
    public void addUserMessage(ChatMessage chatMessage) {
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", chatMessage.getContent());
        if (StringUtils.isNotBlank(chatMessage.getThinkingContent())) {
            userMessage.put("reasoning_content", chatMessage.getThinkingContent());
        }
        this.messages.add(userMessage);
    }

    public static String buildTextDocumentXml(List<ChatMediaText> chatMediaTextList) {
        if (CollectionUtils.isEmpty(chatMediaTextList)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMediaText item : chatMediaTextList) {
            String content = MediaCacheHandler.convertUrlToText(item.getUrl());
            if (StringUtils.isBlank(content)) {
                continue;
            }
            sb.append("<document name=\"").append(escapeXml(item.getName()));
            if (StringUtils.isNotBlank(item.getRelativePath())) {
                sb.append("\" path=\"").append(escapeXml(item.getRelativePath()));
            }
            sb.append("\">\\n").append(content).append("\\n</document>\\n");
        }
        return sb.toString();
    }

    public static String escapeXml(String text) {
        if (StringUtils.isBlank(text)) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public void addMultiUserMessage(String text, List<Pair<String, String>> attachList) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(text);
        addMultiUserMessage(chatMessage, attachList, null);
    }

    public void addMultiUserMessage(ChatMessage chatMessage, List<Pair<String, String>> attachList, List<ChatMediaText> chatMediaTextList) {
        String textDocXml = buildTextDocumentXml(chatMediaTextList);
        String fullText = Optional.ofNullable(chatMessage.getContent()).orElse(StringUtils.EMPTY) + textDocXml;
        Map<String, Object> userMessageMap = new LinkedHashMap<>();
        userMessageMap.put("role", "user");
        List<JSONObject> contentList = new ArrayList<>();
        if (StringUtils.isNotBlank(fullText)) {
            if (CollectionUtils.isNotEmpty(attachList)) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("type", "text");
                jsonObject.put("text", fullText);
                contentList.add(jsonObject);
            } else {
                userMessageMap.put("content", fullText);
            }
        }
        if (CollectionUtils.isNotEmpty(attachList)) {
            contentList.addAll(CompletableFutureUtil.asyncApplyAllOf(attachList, ChatHistory::getAttachJsonObject,
                    SpringContextUtil.getBean(ThreadPoolConstants.CONVERT_URL_BASE64_NAME, ExecutorService.class)).join());
        }
        if (CollectionUtils.isNotEmpty(contentList)) {
            userMessageMap.put("content", contentList);
        }
        this.messages.add(userMessageMap);
    }

    @NotNull
    private static JSONObject getAttachJsonObject(Pair<String, String> attachPair) {
        JSONObject attachJSONObject = new JSONObject();
        attachJSONObject.fluentPut("type", attachPair.getKey());
        JSONObject urlJSONObject = new JSONObject();
        String value = MediaCacheHandler.convertUrlToBase64(attachPair.getValue());
        if ("input_audio".equals(attachPair.getKey())) {
            urlJSONObject.put("data", value);
            int lastDotIndex = attachPair.getKey().lastIndexOf('.');
            urlJSONObject.put("format", lastDotIndex == -1 ? "mp3" : attachPair.getKey().substring(lastDotIndex + 1));
        } else {
            urlJSONObject.put("url", value);
            attachJSONObject.put(attachPair.getKey(), urlJSONObject);
        }
        return attachJSONObject;
    }

    /**
     * 添加用户的发言（包含图片和文本）。
     *
     * @param text               用户输入的文本。
     * @param base64ImageDataUrl 经过 Base64 编码的图片 Data URI。
     */
    public void addMultiModalUserMessage(String text, String base64ImageDataUrl) {
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");

        // 多模态消息的内容是一个列表
        List<Map<String, Object>> contentList = new ArrayList<>();

        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", text);

        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        Map<String, String> imageUrl = new LinkedHashMap<>();
        imageUrl.put("url", base64ImageDataUrl);
        imagePart.put("image_url", imageUrl);

        contentList.add(textPart);
        contentList.add(imagePart);

        userMessage.put("content", contentList);
        this.messages.add(userMessage);
    }

    /**
     * 添加AI助手的回答，用于保存上一轮的对话结果。
     *
     * @param text 模型返回的文本内容。
     */
    public void addAssistantMessage(String text) {
        this.addAssistantMessage(text, null);
    }

    public void addAssistantMessage(String text, String reasoningContent) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(text);
        chatMessage.setThinkingContent(reasoningContent);
        addAssistantMessage(chatMessage);
    }

    public void addAssistantMessage(ChatMessage chatMessage) {
        String text = chatMessage.getContent();
        String reasoningContent = chatMessage.getThinkingContent();
        Map<String, Object> assistantMessage = new LinkedHashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", text);
        if (StringUtils.isNotBlank(reasoningContent)) {
            assistantMessage.put("reasoning_content", reasoningContent);
        }
        this.messages.add(assistantMessage);
    }


    /**
     * 获取完整的对话历史列表，用于发送给API。
     * <p>
     * 截断时从最近的 user 消息开始对齐：若截断起点落在 assistant 消息上（其对应的 user
     * 问题已被丢弃），则再向后移动一条，避免向模型发送没有上文问题的孤立回答。
     * 返回深拷贝副本，防止外部修改影响内部状态。
     *
     * @return 消息列表。
     */
    public List<Map<String, Object>> getHistory() {
        boolean hasSystem = !messages.isEmpty() && "system".equals(messages.get(0).get("role"));
        int startIndex = Math.max(hasSystem ? 1 : 0, messages.size() - limit);
        // 对齐：截断后首条不应是 assistant（避免丢失其对应的 user 问题）
        if (startIndex < messages.size() && "assistant".equals(messages.get(startIndex).get("role"))) {
            startIndex++;
        }
        List<Map<String, Object>> historyList = new ArrayList<>();
        if (hasSystem) {
            // 系统消息始终保留在头部
            historyList.add(new LinkedHashMap<>(messages.get(0)));
        }
        for (int i = startIndex; i < messages.size(); i++) {
            historyList.add(new LinkedHashMap<>(messages.get(i))); // 深拷贝，防止外部修改
        }
        return historyList;
    }


    /**
     * 清空历史，开始新的会话。
     */
    public void clear() {
        this.messages.clear();
    }
}