package com.grw.xiaobai.hybrid.llm.config;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.constant.ApplicationConstants;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Getter
@Setter
public class ApplicationConfig {
    private static final String CONFIG_FILE = "application.json";

    private List<CustomModel> models = Lists.newArrayList();

    private LlamaPathConfig llamaPathConfig = new LlamaPathConfig();

    private String nvidiaSmiPath;

    private String accountPassword;

    private UserConfig userConfig = new UserConfig();

    @Value("${llm.version:unknown}")
    @JSONField(serialize = false, deserialize = false)
    private String version;

    @JSONField(serialize = false, deserialize = false)
    private ApplicationConfig oldApplicationConfig;
    private List<ConfigChangedListener> configChangedListenerList = Lists.newArrayList();

    static {
        try {
            Files.createDirectories(Paths.get(FilePathConstants.FILE_MANAGER_DIR));
        } catch (IOException e) {
            throw new RuntimeException("failed to create " + FilePathConstants.FILE_MANAGER_DIR);
        }
    }

    public void addListener(ConfigChangedListener configChangedListener) {
        configChangedListenerList.add(configChangedListener);
    }


    public CustomModel matchCustomModel(String modelName) {
        List<CustomModel> customModelList = this.models;
        if (CollectionUtils.isEmpty(customModelList)) {
            return null;
        }
        for (CustomModel customModel : customModelList) {
            Pattern pattern = Pattern.compile(customModel.regex, Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(modelName).matches()) {
                return customModel;
            }
        }
        return null;
    }

    @PostConstruct
    public void init() {
        File configFile = new File(FilePathConstants.FILE_MANAGER_DIR, CONFIG_FILE);
        if (!configFile.exists()) {
            log.info("Application config file not found, creating empty config: {}", configFile.getAbsolutePath());
            FileUtil.writeString("{}", configFile, StandardCharsets.UTF_8);
        }
        loadFromJson();
        oldApplicationConfig = cloneConfig();
    }


    private ApplicationConfig cloneConfig() {
        ApplicationConfig copy = new ApplicationConfig();
        // 注意：通过 Getter 读数据，防止 this 是代理对象
        copy.setNvidiaSmiPath(this.getNvidiaSmiPath());
        copy.setAccountPassword(this.getAccountPassword());

        // 内部对象也需要深度复制，防止引用污染
        if (this.getLlamaPathConfig() != null) {
            ApplicationConfig.LlamaPathConfig llamaCopy = new ApplicationConfig.LlamaPathConfig();
            llamaCopy.setLlamaModelPath(this.getLlamaPathConfig().getLlamaModelPath());
            llamaCopy.setLlamaExecPath(this.getLlamaPathConfig().getLlamaExecPath());
            llamaCopy.setIkLlamaExecPath(this.getLlamaPathConfig().getIkLlamaExecPath());
            copy.setLlamaPathConfig(llamaCopy);
        }

        if (this.getUserConfig() != null) {
            ApplicationConfig.UserConfig userCopy = new ApplicationConfig.UserConfig();
            userCopy.setGuestRegisterEnabled(this.getUserConfig().isGuestRegisterEnabled());
            userCopy.setGuestRegisterDurationS(this.getUserConfig().getGuestRegisterDurationS());
            userCopy.setUserRegisterDurationS(this.getUserConfig().getUserRegisterDurationS());
            copy.setUserConfig(userCopy);
        }

        if (this.getModels() != null) {
            copy.setModels(Lists.newArrayList(this.getModels()));
        }

        return copy;
    }


    private synchronized void loadFromJson() {
        File configFile = new File(FilePathConstants.FILE_MANAGER_DIR, CONFIG_FILE);
        if (!configFile.exists()) {
            return;
        }
        try {
            String jsonContent = FileUtil.readString(configFile, StandardCharsets.UTF_8);
            ApplicationConfig loaded = JSON.parseObject(jsonContent, ApplicationConfig.class);
            if (loaded != null) {
                updateFrom(loaded);
            }
            log.info("ApplicationConfig loaded successfully from {}||loaded models size {}",
                    configFile.getAbsolutePath(), Optional.of(loaded.models).map(List::size).orElse(0));
        } catch (Exception e) {
            log.error("Failed to load ApplicationConfig from {}", configFile.getAbsolutePath(), e);
        }
    }

    private void updateFrom(ApplicationConfig loaded) {
        if (loaded.llamaPathConfig != null) {
            this.llamaPathConfig = loaded.llamaPathConfig;
        }
        this.nvidiaSmiPath = loaded.nvidiaSmiPath;
        this.accountPassword = loaded.accountPassword;
        if (loaded.userConfig != null) {
            this.userConfig = loaded.userConfig;
        }
        this.models = Optional.ofNullable(loaded.models).orElse(Lists.newArrayList());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationConfig that = (ApplicationConfig) o;
        return Objects.equals(models, that.models) && Objects.equals(llamaPathConfig, that.llamaPathConfig) && Objects.equals(nvidiaSmiPath, that.nvidiaSmiPath) && Objects.equals(accountPassword, that.accountPassword) && Objects.equals(userConfig, that.userConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(models, llamaPathConfig, nvidiaSmiPath, accountPassword, userConfig);
    }

    public void save() {
        try {
            File configFile = new File(FilePathConstants.FILE_MANAGER_DIR, CONFIG_FILE);
            String json = JSON.toJSONString(this);
            FileUtil.writeString(json, configFile, StandardCharsets.UTF_8);
            configChanged();
            log.info("ApplicationConfig saved successfully");
        } catch (Exception e) {
            log.error("Failed to save ApplicationConfig", e);
        }
    }

    private void configChanged() {
        for (ConfigChangedListener configChangedListener : configChangedListenerList) {
            configChangedListener.configChanged(oldApplicationConfig, this);
        }
        oldApplicationConfig = cloneConfig();
    }

    public void setConfigValue(String key, String value) {
        switch (key) {
            case ApplicationConstants.LLAMA_MODEL_PATH:
                llamaPathConfig.setLlamaModelPath(value);
                break;
            case ApplicationConstants.LLAMA_EXEC_PATH:
                llamaPathConfig.setLlamaExecPath(value);
                break;
            case ApplicationConstants.IK_LLAMA_EXEC_PATH:
                llamaPathConfig.setIkLlamaExecPath(value);
                break;
            case ApplicationConstants.NVIDIA_SMI_PATH:
                this.nvidiaSmiPath = value;
                break;
            case ApplicationConstants.GUEST_REGISTER_ENABLED:
                userConfig.setGuestRegisterEnabled(Boolean.parseBoolean(value));
                break;
            case ApplicationConstants.GUEST_REGISTER_DURATION:
                userConfig.setGuestRegisterDurationS(Long.parseLong(value));
                break;
            case ApplicationConstants.USER_REGISTER_DURATION:
                userConfig.setUserRegisterDurationS(Long.parseLong(value));
                break;
            case ApplicationConstants.ACCOUNT_PASSWORD:
                this.accountPassword = value;
                break;
            default:
                log.warn("Unknown config key: {}", key);
                break;
        }
    }


    @Data
    public static class CustomModel {
        private String regex;
        private int contextSize;
        private int layer;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            CustomModel that = (CustomModel) o;
            return contextSize == that.contextSize && layer == that.layer && Objects.equals(regex, that.regex);
        }

        @Override
        public int hashCode() {
            return Objects.hash(regex, contextSize, layer);
        }
    }

    @Data
    public static class LlamaPathConfig {
        private String llamaModelPath;
        private String llamaExecPath;
        private String ikLlamaExecPath;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            LlamaPathConfig that = (LlamaPathConfig) o;
            return Objects.equals(llamaModelPath, that.llamaModelPath) && Objects.equals(llamaExecPath, that.llamaExecPath) && Objects.equals(ikLlamaExecPath, that.ikLlamaExecPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(llamaModelPath, llamaExecPath, ikLlamaExecPath);
        }
    }

    @Data
    public static class UserConfig {
        private boolean guestRegisterEnabled = false;
        private long guestRegisterDurationS = 2L * 24 * 60 * 60;
        private long userRegisterDurationS = 365L * 24 * 60 * 60;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            UserConfig that = (UserConfig) o;
            return guestRegisterEnabled == that.guestRegisterEnabled && guestRegisterDurationS == that.guestRegisterDurationS && userRegisterDurationS == that.userRegisterDurationS;
        }

        @Override
        public int hashCode() {
            return Objects.hash(guestRegisterEnabled, guestRegisterDurationS, userRegisterDurationS);
        }
    }

    public interface ConfigChangedListener {
        void configChanged(ApplicationConfig old, ApplicationConfig newConfig);
    }

}
