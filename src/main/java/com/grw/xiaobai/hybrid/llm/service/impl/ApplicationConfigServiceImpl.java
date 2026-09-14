package com.grw.xiaobai.hybrid.llm.service.impl;

import com.grw.xiaobai.hybrid.llm.config.ApplicationConfig;
import com.grw.xiaobai.hybrid.llm.constant.ApplicationConstants;
import com.grw.xiaobai.hybrid.llm.exception.BusinessLogicException;
import com.grw.xiaobai.hybrid.llm.service.ApplicationConfigService;
import com.grw.xiaobai.hybrid.llm.service.SystemService;
import com.grw.xiaobai.hybrid.llm.utils.CommandExecutor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ApplicationConfigServiceImpl implements ApplicationConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationConfigServiceImpl.class);
    private static final String KEY_NVIDIA_SMI_PATH = ApplicationConstants.NVIDIA_SMI_PATH;
    private static final String KEY_GUEST_REGISTER_ENABLED = ApplicationConstants.GUEST_REGISTER_ENABLED;
    private static final String KEY_GUEST_REGISTER_DURATION = ApplicationConstants.GUEST_REGISTER_DURATION;
    private static final String KEY_USER_REGISTER_DURATION = ApplicationConstants.USER_REGISTER_DURATION;
    private static final String KEY_ACCOUNT_PASSWORD = ApplicationConstants.ACCOUNT_PASSWORD;

    @Resource
    private ApplicationConfig applicationConfig;

    @Resource
    private SystemService systemService;

    public ApplicationConfig getApplicationConfig() {
        return applicationConfig;
    }

    public boolean isGuestRegisterEnabled() {
        return applicationConfig.getUserConfig().isGuestRegisterEnabled();
    }

    public long getGuestRegisterDurationDays() {
        return applicationConfig.getUserConfig().getGuestRegisterDurationS() / 86400;
    }

    @Override
    public String getVersion() {
        return applicationConfig.getVersion();
    }

    public String getConfigValue(String key) {
        switch (key) {
            case KEY_NVIDIA_SMI_PATH:
                return applicationConfig.getNvidiaSmiPath();
            case KEY_GUEST_REGISTER_ENABLED:
                return String.valueOf(applicationConfig.getUserConfig().isGuestRegisterEnabled());
            case KEY_GUEST_REGISTER_DURATION:
                return String.valueOf(applicationConfig.getUserConfig().getGuestRegisterDurationS());
            case KEY_USER_REGISTER_DURATION:
                return String.valueOf(applicationConfig.getUserConfig().getUserRegisterDurationS());
            case KEY_ACCOUNT_PASSWORD:
                return applicationConfig.getAccountPassword();
            default:
                return null;
        }
    }

    public void saveConfigValue(String key, String value) {
        if (StringUtils.isBlank(value)) {
            applicationConfig.setConfigValue(key, value);
            applicationConfig.save();
            return;
        }

        switch (key) {
            case KEY_NVIDIA_SMI_PATH: {
                String expectedName = systemService.isWindows() ? "nvidia-smi.exe" : "nvidia-smi";
                File nvidiaSmiFile = new File(value);
                if (value.contains(" ")) {
                    throw BusinessLogicException.builder().msgKey("config.nvidia_smi.path_contains_spaces").msg("nvidia-smi path must not contain spaces: " + value).build();
                } else if (!nvidiaSmiFile.exists()) {
                    throw BusinessLogicException.builder().msgKey("config.nvidia_smi.file_not_found").msg("nvidia-smi file not found: " + value).build();
                } else if (!nvidiaSmiFile.getName().equals(expectedName)) {
                    throw BusinessLogicException.builder().msgKey("config.nvidia_smi.incorrect_filename").msg("Incorrect filename, expected: " + expectedName).build();
                } else if (!nvidiaSmiFile.canExecute()) {
                    throw BusinessLogicException.builder().msgKey("config.nvidia_smi.not_executable").msg("nvidia-smi file is not executable: " + value).build();
                }
                break;
            }
            case KEY_GUEST_REGISTER_ENABLED:
                break;
            case KEY_GUEST_REGISTER_DURATION:
            case KEY_USER_REGISTER_DURATION: {
                long parsed;
                try {
                    parsed = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw BusinessLogicException.builder().msgKey("config.duration.must_be_numeric").msg("Duration must be a number").build();
                }
                if (parsed <= 0) {
                    throw BusinessLogicException.builder().msgKey("config.duration.must_be_positive").msg("Duration must be greater than 0").build();
                }
                break;
            }
            case KEY_ACCOUNT_PASSWORD:
                if (!systemService.isWindows()) {
                    String validateCommand = "echo \"" + value.replace("\"", "\\\"") + "\" | sudo -S true";
                    CommandExecutor.CommandResult result = CommandExecutor.executeCommand(validateCommand, TimeUnit.SECONDS.toMillis(10));
                    if (!result.isSuccess()) {
                        LOGGER.warn("account password is incorrect ,error result={}", result.getStdErrorOutput());
                        throw BusinessLogicException.builder().msgKey("config.account_password.incorrect").msg("Account password is incorrect, please retry").build();
                    }
                }
                break;
            default:
                throw BusinessLogicException.builder().msgKey("config.unknown_key").msg("Unknown config key: " + key).build();
        }

        applicationConfig.setConfigValue(key, value);
        applicationConfig.save();
    }

    public Map<String, Boolean> validatePaths() {
        Map<String, Boolean> result = new java.util.HashMap<>();
        validateLlamaModelPath(result);
        validateLlamaExecPath(result);
        validateIkLlamaExecPath(result);
        validateNvidiaSmi(result);
        return result;
    }

    private void validateLlamaModelPath(Map<String, Boolean> result) {
        String path = applicationConfig.getLlamaPathConfig().getLlamaModelPath();
        if (StringUtils.isBlank(path)) {
            result.put(ApplicationConstants.LLAMA_MODEL_PATH, false);
            return;
        }
        File dir = new File(path);
        result.put(ApplicationConstants.LLAMA_MODEL_PATH, dir.exists() && dir.isDirectory());
    }

    private void validateLlamaExecPath(Map<String, Boolean> result) {
        String path = applicationConfig.getLlamaPathConfig().getLlamaExecPath();
        if (StringUtils.isBlank(path)) {
            result.put(ApplicationConstants.LLAMA_EXEC_PATH, false);
            return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            result.put(ApplicationConstants.LLAMA_EXEC_PATH, false);
            return;
        }
        String serverName = systemService.isWindows() ? "llama-server.exe" : "llama-server";
        String splitName = systemService.isWindows() ? "llama-gguf-split.exe" : "llama-gguf-split";
        result.put(ApplicationConstants.LLAMA_EXEC_PATH,
                new File(dir, serverName).exists() && new File(dir, splitName).exists());
    }

    private void validateIkLlamaExecPath(Map<String, Boolean> result) {
        String path = applicationConfig.getLlamaPathConfig().getIkLlamaExecPath();
        if (StringUtils.isBlank(path)) {
            result.put(ApplicationConstants.IK_LLAMA_EXEC_PATH, true);
            return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            result.put(ApplicationConstants.IK_LLAMA_EXEC_PATH, false);
            return;
        }
        String serverName = systemService.isWindows() ? "llama-server.exe" : "llama-server";
        String splitName = systemService.isWindows() ? "llama-gguf-split.exe" : "llama-gguf-split";
        result.put(ApplicationConstants.IK_LLAMA_EXEC_PATH,
                new File(dir, serverName).exists() && new File(dir, splitName).exists());
    }

    private void validateNvidiaSmi(Map<String, Boolean> result) {
        String path = applicationConfig.getNvidiaSmiPath();
        if (StringUtils.isBlank(path)) {
            result.put(ApplicationConstants.NVIDIA_SMI_PATH, false);
            return;
        }
        String expectedName = systemService.isWindows() ? "nvidia-smi.exe" : "nvidia-smi";
        File file = new File(path);
        result.put(ApplicationConstants.NVIDIA_SMI_PATH,
                file.exists() && file.getName().equals(expectedName) && file.canExecute());
    }

    public void saveModels(List<ApplicationConfig.CustomModel> models) {
        List<String> errors = new ArrayList<>();
        if (models != null) {
            for (int i = 0; i < models.size(); i++) {
                ApplicationConfig.CustomModel cm = models.get(i);
                String prefix = "Item " + (i + 1);
                if (cm.getRegex() == null || cm.getRegex().trim().isEmpty()) {
                    errors.add(prefix + ": Regex must not be empty");
                } else {
                    try {
                        java.util.regex.Pattern.compile(cm.getRegex(), java.util.regex.Pattern.CASE_INSENSITIVE);
                    } catch (java.util.regex.PatternSyntaxException e) {
                        errors.add(prefix + ": Invalid regex - " + e.getDescription());
                    }
                }
                if (cm.getContextSize() <= 0) {
                    errors.add(prefix + ": Context size must be greater than 0");
                }
                if (cm.getLayer() <= 0) {
                    errors.add(prefix + ": Layer count must be greater than 0");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw BusinessLogicException.builder().msgKey("config.model.validation_failed").msg(String.join("\n", errors)).build();
        }
        applicationConfig.setModels(models != null ? models : Collections.emptyList());
        applicationConfig.save();
    }

    public void saveLlamaPaths(Map<String, String> body) {
        List<String> requiredErrors = new ArrayList<>();
        List<String> optionalWarnings = new ArrayList<>();
        String llamaModelPath = body.get(ApplicationConstants.LLAMA_MODEL_PATH);
        String llamaExecPath = body.get(ApplicationConstants.LLAMA_EXEC_PATH);
        String ikLlamaExecPath = body.get(ApplicationConstants.IK_LLAMA_EXEC_PATH);
        String nvidiaSmiPath = body.get(ApplicationConstants.NVIDIA_SMI_PATH);

        if (StringUtils.isBlank(llamaModelPath)) {
            requiredErrors.add("Llama model directory must not be empty");
        } else {
            if (llamaModelPath.contains(" ")) {
                requiredErrors.add("Llama model directory path must not contain spaces: " + llamaModelPath);
            } else {
                File modelDir = new File(llamaModelPath);
                if (!modelDir.exists() || !modelDir.isDirectory()) {
                    requiredErrors.add("Llama model directory not found: " + llamaModelPath);
                }
            }
        }

        if (StringUtils.isBlank(llamaExecPath)) {
            requiredErrors.add("Llama.cpp execution directory must not be empty");
        } else {
            if (llamaExecPath.contains(" ")) {
                requiredErrors.add("Llama.cpp execution directory path must not contain spaces: " + llamaExecPath);
            } else {
                File execDir = new File(llamaExecPath);
                if (!execDir.exists() || !execDir.isDirectory()) {
                    requiredErrors.add("Execution directory not found: " + llamaExecPath);
                } else {
                    validateExecDir(execDir, "llama.cpp", requiredErrors);
                }
            }
        }

        if (StringUtils.isNotBlank(ikLlamaExecPath)) {
            if (ikLlamaExecPath.contains(" ")) {
                optionalWarnings.add("ik_llama.cpp execution directory path must not contain spaces: " + ikLlamaExecPath);
            } else {
                File ikExecDir = new File(ikLlamaExecPath);
                if (!ikExecDir.exists() || !ikExecDir.isDirectory()) {
                    optionalWarnings.add("ik_llama.cpp execution directory not found: " + ikLlamaExecPath);
                } else {
                    validateExecDir(ikExecDir, "ik_llama.cpp", optionalWarnings);
                }
            }
        }

        if (StringUtils.isNotBlank(nvidiaSmiPath)) {
            if (nvidiaSmiPath.contains(" ")) {
                optionalWarnings.add("nvidia-smi path must not contain spaces: " + nvidiaSmiPath);
            } else {
                String expectedName = systemService.isWindows() ? "nvidia-smi.exe" : "nvidia-smi";
                File nvidiaSmiFile = new File(nvidiaSmiPath);
                if (!nvidiaSmiFile.getName().equals(expectedName)) {
                    optionalWarnings.add("nvidia-smi filename incorrect, expected: " + expectedName);
                } else if (!nvidiaSmiFile.exists()) {
                    optionalWarnings.add("nvidia-smi file not found: " + nvidiaSmiPath);
                } else if (!nvidiaSmiFile.canExecute()) {
                    optionalWarnings.add("nvidia-smi file is not executable: " + nvidiaSmiPath);
                }
            }
        }

        if (!optionalWarnings.isEmpty()) {
            LOGGER.warn("Optional path validation warnings: {}", String.join("; ", optionalWarnings));
            throw BusinessLogicException.builder().msgKey("config.optional_validation_failed").msg("Optional configuration validation failed").logicErrorDetail(String.join("; ", optionalWarnings)).build();
        }

        if (!requiredErrors.isEmpty()) {
            throw BusinessLogicException.builder().msgKey("config.required_path_validation_failed").msg(String.join("\n", requiredErrors)).build();
        }

        applicationConfig.getLlamaPathConfig().setLlamaModelPath(llamaModelPath);
        applicationConfig.getLlamaPathConfig().setLlamaExecPath(llamaExecPath);
        applicationConfig.getLlamaPathConfig().setIkLlamaExecPath(ikLlamaExecPath);
        applicationConfig.setNvidiaSmiPath(nvidiaSmiPath);
        applicationConfig.save();
    }

    private void validateExecDir(File execDir, String label, List<String> errors) {
        String serverName = systemService.isWindows() ? "llama-server.exe" : "llama-server";
        String splitName = systemService.isWindows() ? "llama-gguf-split.exe" : "llama-gguf-split";
        if (!new File(execDir, serverName).exists()) {
            errors.add(label + " directory is missing " + serverName + " file");
        }
        if (!new File(execDir, splitName).exists()) {
            errors.add(label + " directory is missing " + splitName + " file");
        }
    }
}
