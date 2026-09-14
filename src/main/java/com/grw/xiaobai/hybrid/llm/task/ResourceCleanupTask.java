package com.grw.xiaobai.hybrid.llm.task;

import com.grw.xiaobai.hybrid.llm.service.ChatService;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResourceCleanupTask {

    @Resource
    private ChatService chatService;

    @Scheduled(cron = "0 0 3,11,18 * * ?")
    public void cleanupExpiredSessions() {
        log.info("Starting cleanup of expired chat sessions...");
        int deleted = chatService.cleanupExpiredChatSession();
        log.info("Deleted {} expired sessions", deleted);
    }


}
