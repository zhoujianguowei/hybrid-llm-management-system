package com.grw.xiaobai.hybrid.llm.task;

import com.grw.xiaobai.hybrid.llm.service.UserService;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserManagerTask {
    @Resource
    private UserService userService;

    @Scheduled(cron = "0 */1 * * * *")
    public void checkExpiredUsers() {
        userService.checkRegisterEnd();
    }
}
