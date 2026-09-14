package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.entity.system.SystemInfo;

public interface SystemService {
    SystemInfo getSystemInfo();

    String getSystem();

    boolean isWindows();

    boolean isLinux();

    boolean isMac();

    String getSystemFileName(String fileName);
}
