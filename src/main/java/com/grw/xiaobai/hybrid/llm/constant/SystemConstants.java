package com.grw.xiaobai.hybrid.llm.constant;

public class SystemConstants {

    // mmproj/MTP 支持的量化后缀（大小写不敏感）
    public static final String[] MM_PROJ_QUANT_SUFFIXES = {"f32", "bf16", "f16", "q8_0"};

    public static final String PATH_SEPARATOR = "/";
    public static final String WINDOWS_FILE_SEPARATOR = "\\";

    public static final String WINDOWS_SYSTEM = "windows";
    public static final String LINUX_SYSTEM = "linux";
    public static final String MAC_SYSTEM = "mac";

    // 默认会话标题对应的 i18n 消息 key（前端按当前语言翻译）
    public static final String DEFAULT_SESSION_TITLE_KEY = "chat.new_conversation";

}
