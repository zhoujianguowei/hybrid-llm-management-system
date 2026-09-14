package com.grw.xiaobai.hybrid.llm.service.impl;

import com.grw.xiaobai.hybrid.llm.constant.SystemConstants;
import com.grw.xiaobai.hybrid.llm.service.SystemService;
import com.grw.xiaobai.hybrid.llm.entity.system.SystemInfo;
import com.sun.management.OperatingSystemMXBean;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.Locale;

@Service
public class SystemServiceImpl implements SystemService {

    private final String system = detectSystem();

    public SystemInfo getSystemInfo() {
        SystemInfo info = new SystemInfo();
        info.setSystem(system);
        info.setCpuCores(Runtime.getRuntime().availableProcessors());
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        info.setTotalMemoryMb(osBean.getTotalPhysicalMemorySize() / (1024 * 1024));
        return info;
    }

    public String getSystem() {
        return system;
    }

    public boolean isWindows() {
        return SystemConstants.WINDOWS_SYSTEM.equals(system);
    }

    public boolean isLinux() {
        return SystemConstants.LINUX_SYSTEM.equals(system);
    }

    public boolean isMac() {
        return SystemConstants.MAC_SYSTEM.equals(system);
    }

    private String detectSystem() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return SystemConstants.WINDOWS_SYSTEM;
        } else if (os.contains("mac") || os.contains("darwin")) {
            return SystemConstants.MAC_SYSTEM;
        } else {
            return SystemConstants.LINUX_SYSTEM;
        }
    }

    public String getSystemFileName(String fileName) {
        return isWindows() ? fileName + ".exe" : fileName;
    }
}
