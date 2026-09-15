package com.grw.xiaobai.hybrid.llm.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.grw.xiaobai.hybrid.llm.constant.CacheConstants;
import org.springframework.cache.CacheManager;
import org.springframework.cache.guava.GuavaCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
public class GuavaCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        // 创建不同配置的Guava Cache
        Cache<Object, Object> shortTimeCache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();

        Cache<Object, Object> midTimeCache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();

        Cache<Object, Object> longTimeCache = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(200)
                .build();

        // 将Guava Cache包装成Spring Cache
        GuavaCache shortTimeSpringCache = new GuavaCache(CacheConstants.SHORT_TIME_CACHE_NAME, shortTimeCache);
        GuavaCache midTimeSpringCache = new GuavaCache(CacheConstants.MID_TIME_CACHE_NAME, midTimeCache);
        GuavaCache longTimeSpringCache = new GuavaCache(CacheConstants.LONG_TIME_CACHE_NAME, longTimeCache);

        // 注册所有缓存到CacheManager
        cacheManager.setCaches(Arrays.asList(
                shortTimeSpringCache,
                midTimeSpringCache,
                longTimeSpringCache
        ));
        return cacheManager;
    }
}