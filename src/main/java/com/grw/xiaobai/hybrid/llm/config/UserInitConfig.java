package com.grw.xiaobai.hybrid.llm.config;

import com.grw.xiaobai.hybrid.llm.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
public class UserInitConfig implements CommandLineRunner {
    @Resource
    private UserService userService;

    @Override
    public void run(String... args) throws Exception {
        userService.init();
        userService.checkRegisterEnd();
    }
}