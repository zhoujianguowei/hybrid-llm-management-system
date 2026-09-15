package com.grw.xiaobai.hybrid.llm.manager;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.utils.converter.OrikaBeanConverter;
import com.grw.xiaobai.hybrid.llm.entity.chat.OpenApiLLMConfig;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpenApiLLMConfigManager {
    private static final String CONFIG_DIR = FilePathConstants.CONFIG_DIR;
    private static final String CONFIG_FILE = "openapillm_config.json";
    private final List<OpenApiLLMConfig> globalConfigs = new CopyOnWriteArrayList<>();

    static {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
        } catch (IOException e) {
            throw new RuntimeException("failed to create " + CONFIG_DIR);
        }
    }

    @PostConstruct
    public void loadAll() {
        File configFile = new File(CONFIG_DIR, CONFIG_FILE);
        if (!configFile.exists()) {
            log.info("API config file not found, creating empty config: {}", configFile.getAbsolutePath());
            saveToFile(globalConfigs);
            return;
        }

        String json = FileUtil.readString(configFile, StandardCharsets.UTF_8);
        List<OpenApiLLMConfig> configs = JSON.parseArray(json, OpenApiLLMConfig.class);
        if (configs != null) {
            globalConfigs.clear();
            globalConfigs.addAll(configs);
            log.info("Loaded {} API configs", configs.size());
        }
    }

    public List<OpenApiLLMConfig> getConfigs() {
        return new ArrayList<>(globalConfigs);
    }

    public OpenApiLLMConfig saveConfig(OpenApiLLMConfig config) {
        boolean exists = false;
        for (OpenApiLLMConfig existing : globalConfigs) {
            if (existing.getId().equals(config.getId())) {
                OrikaBeanConverter.mergeKeepNonNull(existing, config);
                existing.setUpdateTime(System.currentTimeMillis());
                exists = true;
                break;
            }
        }

        if (!exists) {
            config.setId(UUID.randomUUID().toString().replace("-", ""));
            config.setCreateTime(System.currentTimeMillis());
            config.setUpdateTime(System.currentTimeMillis());
            globalConfigs.add(config);
        }

        saveToFile(globalConfigs);
        return config;
    }

    public boolean deleteConfig(String id) {
        boolean removed = globalConfigs.removeIf(config -> config.getId().equals(id));
        if (removed) {
            saveToFile(globalConfigs);
        }
        return removed;
    }

    public OpenApiLLMConfig getConfig(String id) {
        for (OpenApiLLMConfig config : globalConfigs) {
            if (config.getId().equals(id)) {
                return config;
            }
        }
        return null;
    }

    public boolean baseUrlExists(String baseUrl) {
        for (OpenApiLLMConfig config : globalConfigs) {
            if (config.getBaseUrl().equals(baseUrl)) {
                return true;
            }
        }
        return false;
    }

    public boolean baseUrlExists(String id, String baseUrl) {
        for (OpenApiLLMConfig config : globalConfigs) {
            if (config.getId().equals(id)) continue;
            if (config.getBaseUrl().equals(baseUrl)) {
                return true;
            }
        }
        return false;
    }

    private void saveToFile(List<OpenApiLLMConfig> configs) {
        String filePath = CONFIG_DIR + File.separator + CONFIG_FILE;
        String json = JSON.toJSONString(configs, SerializerFeature.PrettyFormat);
        FileUtil.writeString(json, filePath, StandardCharsets.UTF_8);
        log.info("API configs saved to {}, {} configs", filePath, configs.size());
    }

}