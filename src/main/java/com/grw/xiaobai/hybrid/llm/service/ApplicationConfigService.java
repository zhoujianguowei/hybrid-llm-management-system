package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.config.ApplicationConfig;

import java.util.List;
import java.util.Map;

public interface ApplicationConfigService {
    ApplicationConfig getApplicationConfig();

    boolean isGuestRegisterEnabled();

    long getGuestRegisterDurationDays();

    String getVersion();

    String getConfigValue(String key);

    void saveConfigValue(String key, String value);

    Map<String, Boolean> validatePaths();

    void saveModels(List<ApplicationConfig.CustomModel> models);

    void saveLlamaPaths(Map<String, String> body);
}
