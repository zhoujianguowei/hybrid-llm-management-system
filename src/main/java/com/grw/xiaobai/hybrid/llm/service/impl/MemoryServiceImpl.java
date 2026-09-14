package com.grw.xiaobai.hybrid.llm.service.impl;

import cn.hutool.core.lang.Pair;
import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.entity.system.MemoryInfo;
import com.grw.xiaobai.hybrid.llm.service.MemoryService;
import com.grw.xiaobai.hybrid.llm.service.SystemService;
import com.grw.xiaobai.hybrid.llm.utils.CommandExecutor;
import com.grw.xiaobai.hybrid.llm.utils.CommandExecutor.CommandResult;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MemoryServiceImpl implements MemoryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryServiceImpl.class);
    private static final int MAX_CACHED_HISTORY_COUNT = 60;

    @Resource
    private SystemService systemService;

    private final LinkedList<Pair<Long, MemoryInfo>> cachedMemoryHistory = Lists.newLinkedList();

    public List<Pair<Long, MemoryInfo>> getMemoryHistory() {
        synchronized (this) {
            return new ArrayList<>(cachedMemoryHistory);
        }
    }

    public MemoryInfo getCurrentMemoryInfo() {
        if (systemService.isLinux()) {
            return getLinuxMemory();
        } else if (systemService.isMac()) {
            return getMacMemory();
        } else {
            return getFallbackMemory();
        }
    }

    private MemoryInfo getLinuxMemory() {
        try {
            CommandResult result = CommandExecutor.executeCommand("cat /proc/meminfo", 5000);
            if (!result.isSuccess() || result.getStdOutput() == null) {
                LOGGER.warn("Failed to read /proc/meminfo");
                return getFallbackMemory();
            }
            long totalKb = 0;
            long availableKb = 0;
            for (String line : result.getStdOutput().split("\n")) {
                if (line.startsWith("MemTotal:")) {
                    totalKb = parseMeminfoValue(line);
                } else if (line.startsWith("MemAvailable:")) {
                    availableKb = parseMeminfoValue(line);
                }
                if (totalKb > 0 && availableKb > 0) break;
            }
            if (totalKb > 0 && availableKb > 0) {
                long totalMb = totalKb / 1024;
                long freeMb = availableKb / 1024;
                long usedMb = totalMb - freeMb;
                return new MemoryInfo(totalMb, usedMb, freeMb);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read Linux memory info, falling back", e);
        }
        return getFallbackMemory();
    }

    private long parseMeminfoValue(String line) {
        String[] parts = line.split("\\s+");
        return parts.length >= 2 ? Long.parseLong(parts[1]) : 0;
    }

    private MemoryInfo getMacMemory() {
        try {
            CommandResult totalResult = CommandExecutor.executeCommand("sysctl -n hw.memsize", 5000);
            if (!totalResult.isSuccess() || totalResult.getStdOutput() == null) {
                LOGGER.warn("Failed to get hw.memsize on macOS");
                return getFallbackMemory();
            }
            long totalBytes = Long.parseLong(totalResult.getStdOutput().trim());

            CommandResult vmStatResult = CommandExecutor.executeCommand("vm_stat", 5000);
            if (!vmStatResult.isSuccess() || vmStatResult.getStdOutput() == null) {
                LOGGER.warn("Failed to run vm_stat on macOS");
                return getFallbackMemory();
            }

            long pageSize = 4096;
            long pagesFree = 0, pagesInactive = 0, pagesSpeculative = 0;
            Pattern pattern = Pattern.compile("([^:]+):\\s+([0-9]+)\\.");

            for (String line : vmStatResult.getStdOutput().split("\n")) {
                if (line.contains("page size of")) {
                    Matcher m = Pattern.compile("page size of ([0-9]+) bytes").matcher(line);
                    if (m.find()) pageSize = Long.parseLong(m.group(1));
                    continue;
                }
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    String key = m.group(1).trim();
                    long val = Long.parseLong(m.group(2));
                    switch (key) {
                        case "Pages free":          pagesFree = val; break;
                        case "Pages inactive":      pagesInactive = val; break;
                        case "Pages speculative":   pagesSpeculative = val; break;
                    }
                }
            }

            if (totalBytes > 0) {
                long totalMb = totalBytes / (1024 * 1024);
                long availableBytes = (pagesFree + pagesInactive + pagesSpeculative) * pageSize;
                long freeMb = availableBytes / (1024 * 1024);
                long usedMb = totalMb - freeMb;
                return new MemoryInfo(totalMb, usedMb, freeMb);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read macOS memory info, falling back", e);
        }
        return getFallbackMemory();
    }

    private MemoryInfo getFallbackMemory() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long totalBytes = osBean.getTotalPhysicalMemorySize();
            long freeBytes = osBean.getFreePhysicalMemorySize();
            long totalMb = totalBytes / (1024 * 1024);
            long freeMb = freeBytes / (1024 * 1024);
            long usedMb = totalMb - freeMb;
            return new MemoryInfo(totalMb, usedMb, freeMb);
        } catch (Exception e) {
            LOGGER.error("Fallback memory check failed", e);
        }
        return new MemoryInfo(0, 0, 0);
    }

    public void syncMemoryInfo() {
        MemoryInfo currentInfo = getCurrentMemoryInfo();
        if (currentInfo == null) return;

        long timestamp = System.currentTimeMillis();
        synchronized (this) {
            if (cachedMemoryHistory.size() >= MAX_CACHED_HISTORY_COUNT) {
                cachedMemoryHistory.pollFirst();
            }
            cachedMemoryHistory.addLast(new Pair<>(timestamp, currentInfo));
        }
    }

}
