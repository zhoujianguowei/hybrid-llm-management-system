package com.grw.xiaobai.hybrid.llm.entity.chat;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

@Data
public class RunningChatApiParamWrapper {
    private AtomicBoolean stopFlag = new AtomicBoolean(false);
    private AtomicBoolean generatingClaimed = new AtomicBoolean(false);
    private OpenApiClient openApiClient;
    private ChatMessage latestAssistantMessage;
    private ChatMessage latestUserMessage;
    private StringBuilder latestThinkingContent;
    private StringBuilder latestContent;
    private WebSocketSession activeSocketSession;
    private String sessionId;
    private long lastActiveTime = System.currentTimeMillis();
    private ScheduledFuture<?> heartbeatFuture;
}
