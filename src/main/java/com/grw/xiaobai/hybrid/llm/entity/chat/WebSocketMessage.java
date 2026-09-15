package com.grw.xiaobai.hybrid.llm.entity.chat;

import java.util.Map;

import lombok.Data;

@Data
public class WebSocketMessage {

    private MessageType type;
    private String chatId;
    private String title;
    private String content;
    private String thinking;
    private Long thinkingTimeMs;
    private Boolean success;
    private String message;
    private String msgKey;
    private Map<String, Object> params;
    private ChatSession session;
    private ChatMessage lastAssistantMessage;
    private ChatMessage lastUserMessage;
    private Integer totalTokens;

    public enum MessageType {
        stream_start,
        thinking_chunk,
        content_chunk,
        stream_end,
        clear_history,
        set_system_prompt,
        title_update,
        session_recovery,
        session_expiry,
        error,
        ping,
        pong
    }

    public static WebSocketMessage streamStart() {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.stream_start);
        return msg;
    }

    public static WebSocketMessage thinkingChunk(String thinking) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.thinking_chunk);
        msg.setThinking(thinking);
        return msg;
    }

    public static WebSocketMessage contentChunk(String content) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.content_chunk);
        msg.setContent(content);
        return msg;
    }

    public static WebSocketMessage streamEnd(ChatMessage chatMessage) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.stream_end);
        msg.setLastAssistantMessage(chatMessage);
        return msg;
    }

    public static WebSocketMessage setSystemPrompt(boolean success) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.set_system_prompt);
        msg.setSuccess(success);
        return msg;
    }

    public static WebSocketMessage titleUpdate(String title) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.title_update);
        msg.setTitle(title);
        return msg;
    }

    public static WebSocketMessage sessionRecovery(ChatMessage lastAssistantMessage,ChatMessage latestUserMessage) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.session_recovery);
        msg.setLastAssistantMessage(lastAssistantMessage);
        msg.setLastUserMessage(latestUserMessage);
        return msg;
    }

    public static WebSocketMessage sessionExpiry(String message) {
        return sessionExpiry(null, message, null);
    }

    public static WebSocketMessage sessionExpiry(String msgKey, String message, Map<String, Object> params) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.session_expiry);
        msg.setMsgKey(msgKey);
        msg.setMessage(message);
        msg.setParams(params);
        return msg;
    }

    public static WebSocketMessage error(String message) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.error);
        msg.setMessage(message);
        return msg;
    }

    public static WebSocketMessage error(String msgKey, String message) {
        return error(msgKey, message, null);
    }

    public static WebSocketMessage error(String msgKey, String message, Map<String, Object> params) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(MessageType.error);
        msg.setMsgKey(msgKey);
        msg.setMessage(message);
        msg.setParams(params);
        return msg;
    }
}
