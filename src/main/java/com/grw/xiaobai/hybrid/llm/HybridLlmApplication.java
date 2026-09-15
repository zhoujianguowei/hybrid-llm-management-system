package com.grw.xiaobai.hybrid.llm;

import com.grw.xiaobai.hybrid.llm.handler.GlobalExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JndiDataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.XADataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import springfox.documentation.oas.annotations.EnableOpenApi;

import javax.annotation.Resource;

@EnableScheduling
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        JndiDataSourceAutoConfiguration.class,
        XADataSourceAutoConfiguration.class})
@EnableAsync
@EnableOpenApi
@EnableCaching
public class HybridLlmApplication implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(HybridLlmApplication.class);
    @Resource
    private GlobalExceptionHandler.GlobalUncaughtExceptionHandler uncaughtExceptionHandler;

    public static void main(String[] args) {
        SpringApplication.run(HybridLlmApplication.class, args);
        LOGGER.info("llm command start success");
    }

    // 在应用程序启动后执行
    @Override
    public void run(String... args) throws Exception {
        // 将自定义的处理器设置为所有线程的默认处理器
        Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
        LOGGER.info("已设置 GlobalUncaughtExceptionHandler。");
    }

}