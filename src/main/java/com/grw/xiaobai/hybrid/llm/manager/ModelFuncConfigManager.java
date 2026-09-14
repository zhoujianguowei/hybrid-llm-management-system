package com.grw.xiaobai.hybrid.llm.manager;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.constant.ThinkingConstants;
import com.grw.xiaobai.hybrid.llm.utils.converter.OrikaBeanConverter;
import com.grw.xiaobai.hybrid.llm.entity.chat.ModelFuncConfig;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ModelFuncConfigManager {
    private static final String CONFIG_DIR = FilePathConstants.CONFIG_DIR;
    private static final String CONFIG_FILE = "model_func.json";
    public static final String TYPE_EXACT = "exact";
    public static final String TYPE_REGEX = "regex";
    private final List<ModelFuncConfig> configs = new CopyOnWriteArrayList<>();
    private final List<Runnable> changeListeners = new ArrayList<>();

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void notifyChange() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.warn("Model func config change listener error", e);
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
            log.info("Model func config file not found, creating empty config: {}", configFile.getAbsolutePath());
            saveToFile(configs);
            return;
        }
        String json = FileUtil.readString(configFile, StandardCharsets.UTF_8);
        List<ModelFuncConfig> loaded = JSON.parseArray(json, ModelFuncConfig.class);
        if (loaded != null) {
            configs.clear();
            configs.addAll(loaded);
            log.info("Loaded {} model func configs", loaded.size());
        }
    }

    public List<ModelFuncConfig> getConfigs() {
        return new ArrayList<>(configs);
    }

    public ResultModel<ModelFuncConfig> saveConfig(ModelFuncConfig config) {
        if (TYPE_REGEX.equals(config.getType())) {
            try {
                Pattern.compile(config.getRegex());
            } catch (Exception e) {
                return ResultModel.fail("config.regex_format_error", "Invalid regular expression format: " + e.getMessage());
            }
        }
        // 思考等级校验：若配置了思考参数，则 thinkingLevel 至少需要一个开启档（on level）
        // 关闭档（no_think/none）不再强制要求，部分模型本身不提供非思考模式
        // 混合模式（enableThinking=true）下，off/on 由 enableThinkingParamName 控制，thinkingLevel 本身即强度等级
        if (StringUtils.isNotBlank(config.getThinkParamName())
                && (config.getThinkingLevel() == null || config.getThinkingLevel().isEmpty())) {
            return ResultModel.fail("config.think_level_required", "At least one thinking level is required");
        }
        if (StringUtils.isNotBlank(config.getThinkParamName())
                && !Boolean.TRUE.equals(config.getEnableThinking())
                && config.getThinkingLevel().stream().noneMatch(level -> !ThinkingConstants.OFF_LEVELS.contains(level))) {
            return ResultModel.fail("config.think_level_on_required", "At least one on level is required");
        }
        boolean exists = false;
        for (ModelFuncConfig existing : configs) {
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
            config.setOrder(configs.size());
            configs.add(config);
        }
        saveToFile(configs);
        notifyChange();
        return ResultModel.OK(config);
    }

    public boolean deleteConfig(String id) {
        boolean removed = configs.removeIf(c -> c.getId().equals(id));
        if (removed) {
            saveToFile(configs);
            notifyChange();
        }
        return removed;
    }

    public boolean moveOrder(String id, boolean up) {
        int index = -1;
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).getId().equals(id)) {
                index = i;
                break;
            }
        }
        if (index < 0) return false;
        int targetIndex = up ? index - 1 : index + 1;
        if (targetIndex < 0 || targetIndex >= configs.size()) return false;
        Collections.swap(configs, index, targetIndex);
        for (int i = 0; i < configs.size(); i++) {
            configs.get(i).setOrder(i);
        }
        saveToFile(configs);
        notifyChange();
        return true;
    }

    public ModelFuncConfig getConfig(String id) {
        for (ModelFuncConfig c : configs) {
            if (c.getId().equals(id)) {
                return c;
            }
        }
        return null;
    }

    public ModelFuncConfig matchConfig(String modelName) {
        if (modelName == null || configs.isEmpty()) {
            return null;
        }
        for (ModelFuncConfig c : configs) {
            if (TYPE_EXACT.equals(c.getType()) && modelName.equals(c.getRegex())) {
                return c;
            }
        }
        for (ModelFuncConfig c : configs) {
            if (TYPE_REGEX.equals(c.getType())) {
                try {
                    if (Pattern.compile(c.getRegex(), Pattern.CASE_INSENSITIVE).matcher(modelName).find()) {
                        return c;
                    }
                } catch (Exception e) {
                    log.warn("Invalid regex pattern: {}", c.getRegex());
                }
            }
        }
        return null;
    }

    private void saveToFile(List<ModelFuncConfig> list) {
        String filePath = CONFIG_DIR + File.separator + CONFIG_FILE;
        String json = JSON.toJSONString(list, SerializerFeature.PrettyFormat);
        FileUtil.writeString(json, filePath, StandardCharsets.UTF_8);
        log.info("Model func configs saved to {}, {} configs", filePath, list.size());
    }
}
