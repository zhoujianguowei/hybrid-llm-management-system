package com.grw.xiaobai.hybrid.llm.constant;

import java.io.File;

public class FilePathConstants {

    private static final String USER_HOME = System.getProperty("user.home");
    private static final String S = File.separator;

    public static final String FILE_MANAGER_DIR = USER_HOME + S + ".file_manager";

    public static final String CONFIG_DIR = FILE_MANAGER_DIR + S + "config";
    public static final String USER_CONFIG_DIR = CONFIG_DIR + S + "user";
    public static final String RESOURCE_DIR = FILE_MANAGER_DIR + S + "resource";
    public static final String CHAT_DIR = FILE_MANAGER_DIR + S + "chat";
    public static final String SCRIPTS_DIR = FILE_MANAGER_DIR + S + "scripts";
    public static final String WIN_SCRIPT_DIR = SCRIPTS_DIR + S + "win";

    public static final String USER_JSON_PATH = FILE_MANAGER_DIR + S + "user.json";
    public static final String NOTIFICATION_JSON_PATH = FILE_MANAGER_DIR + S + "blocked_notifications.json";
    public static final String REBOOT_JSON_PATH = FILE_MANAGER_DIR + S + "reboot.json";

    public static final String REBOOT_SCRIPT_PATH = SCRIPTS_DIR + S + "reboot.sh";
    public static final String REBOOT_BAT_PATH = WIN_SCRIPT_DIR + S + "reboot.bat";
    public static final String LLAMA_SCRIPT_PATH = SCRIPTS_DIR + S + "start_llama.sh";
    public static final String VLLM_SCRIPT_PATH = SCRIPTS_DIR + S + "start_vllm.sh";

    public static final String UPLOAD_TEMP_DIR = FILE_MANAGER_DIR + S + "upload_temp";
    public static final String UPLOAD_USER_DIR = FILE_MANAGER_DIR + S + "upload";
    public static final String DOWNLOAD_TEMP_DIR = FILE_MANAGER_DIR + S + "download_temp";
    // llama.cpp 模型运行日志存储目录
    public static final String LLAMA_CPP_LOG_DIR = FILE_MANAGER_DIR + S + "llama.cpp";
    // MTP draft model 子目录路径
    public static final String MTP_DIR = "mtp";
    // mmproj 子目录路径
    public static final String MMPROJ_DIR = "mmproj";

    public static final String PATH_PARAM = "path";
    public static final String PERMISSION_DENIED_KEY = "common.permission_denied";
    public static final String PATH_PROTECTED_DELETE_KEY = "file.path_protected_delete";

}
