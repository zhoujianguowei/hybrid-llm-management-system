package com.grw.xiaobai.hybrid.llm.manager;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.entity.chat.ChatSessionConfig;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
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
public class ChatSessionConfigManager {
    private static final String CONFIG_DIR = FilePathConstants.CONFIG_DIR;
    private static final String CONFIG_FILE = "session_config.json";
    private final List<ChatSessionConfig> configs = new CopyOnWriteArrayList<>();
    private final List<Runnable> changeListeners = new ArrayList<>();

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void notifyChange() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.warn("Session config change listener error", e);
            }
        }
    }

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
            log.info("Session config file not found, creating default configs");
            createDefaultConfigs();
            saveToFile(configs);
            return;
        }
        String json = FileUtil.readString(configFile, StandardCharsets.UTF_8);
        List<ChatSessionConfig> loaded = JSON.parseArray(json, ChatSessionConfig.class);
        if (loaded != null) {
            configs.clear();
            configs.addAll(loaded);
            log.info("Loaded {} session configs", loaded.size());
        }
    }

    private void createDefaultConfigs() {
        configs.add(createConfig(UserRoleEnum.guest, 5, 5, 10, 5, 50, 1));
        configs.add(createConfig(UserRoleEnum.user, 10, 10, 20, 10, 100, 3));
        configs.add(createConfig(UserRoleEnum.admin, 50, 30, 100, 20, 500, 10));
    }

    private ChatSessionConfig createConfig(UserRoleEnum role, int maxAttachSize, int maxTextAttachSize, int maxTextAttachCount,
                                           int maxAttachCount, int maxMessageCount, int pinnedLimit) {
        ChatSessionConfig config = new ChatSessionConfig();
        config.setId(UUID.randomUUID().toString().replace("-", ""));
        config.setRole(role);
        config.setMaxAttachmentSize(maxAttachSize);
        config.setMaxTextAttachmentSize(maxTextAttachSize);
        config.setMaxTextAttachments(maxTextAttachCount);
        config.setMaxAttachments(maxAttachCount);
        config.setMaxMessageCount(maxMessageCount);
        config.setPinnedSessionLimit(pinnedLimit);
        config.setCreateTime(System.currentTimeMillis());
        config.setUpdateTime(System.currentTimeMillis());
        return config;
    }

    public List<ChatSessionConfig> getConfigs() {
        return new ArrayList<>(configs);
    }

    public ChatSessionConfig getConfigByRole(UserRoleEnum role) {
        for (ChatSessionConfig config : configs) {
            if (role.equals(config.getRole())) {
                return config;
            }
        }
        return null;
    }

    public ResultModel<ChatSessionConfig> saveConfig(ChatSessionConfig config) {
        if (config.getMaxAttachmentSize() <= 0 || config.getMaxAttachments() <= 0 || config.getMaxMessageCount() <= 0) {
            return ResultModel.fail("config.limit_must_positive", "All limit values must be greater than 0");
        }
        ChatSessionConfig existing = null;
        for (ChatSessionConfig c : configs) {
            if (c.getId().equals(config.getId())) {
                existing = c;
                break;
            }
        }
        if (existing == null) {
            return ResultModel.fail("config.invalid_session_config", "Invalid session config");
        }
        existing.setMaxAttachmentSize(config.getMaxAttachmentSize());
        existing.setMaxTextAttachmentSize(config.getMaxTextAttachmentSize());
        existing.setMaxTextAttachments(config.getMaxTextAttachments());
        existing.setMaxAttachments(config.getMaxAttachments());
        existing.setMaxMessageCount(config.getMaxMessageCount());
        existing.setPinnedSessionLimit(config.getPinnedSessionLimit());
        existing.setUpdateTime(System.currentTimeMillis());
        saveToFile(configs);
        notifyChange();
        return ResultModel.OK(existing);
    }

    public ChatSessionConfig getConfig(String id) {
        for (ChatSessionConfig c : configs) {
            if (c.getId().equals(id)) {
                return c;
            }
        }
        return null;
    }

    private void saveToFile(List<ChatSessionConfig> list) {
        String filePath = CONFIG_DIR + File.separator + CONFIG_FILE;
        String json = JSON.toJSONString(list, SerializerFeature.PrettyFormat);
        FileUtil.writeString(json, filePath, StandardCharsets.UTF_8);
        log.info("Session configs saved to {}, {} configs", filePath, list.size());
    }
}
