package com.grw.xiaobai.hybrid.llm.service.impl;

import cn.hutool.core.lang.Pair;
import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.config.ApplicationConfig;
import com.grw.xiaobai.hybrid.llm.entity.system.GpuInfo;
import com.grw.xiaobai.hybrid.llm.service.GpuService;
import com.grw.xiaobai.hybrid.llm.utils.CommandExecutor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GpuServiceImpl implements GpuService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GpuServiceImpl.class);
    private static final int MAX_CACHED_HISTORY_COUNT = 60;
    @Resource
    private ApplicationConfig applicationConfig;
    private final LinkedList<Pair<Long, List<GpuInfo>>> cachedGpuHistoryInfoList = Lists.newLinkedList();
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    public List<Pair<Long, List<GpuInfo>>> getGpuHistoryInfo() {
        if (!supportNvidia()) {
            return Lists.newArrayList();
        }
        readWriteLock.readLock().lock();
        try {
            return Lists.newArrayList(cachedGpuHistoryInfoList);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    private boolean supportNvidia() {
        return StringUtils.isNotBlank(applicationConfig.getNvidiaSmiPath());
    }

    public List<GpuInfo> getGpuInfo() {
        if (!supportNvidia()) {
            return Lists.newArrayList();
        }
        String command = applicationConfig.getNvidiaSmiPath() + " --query-gpu=index,name,memory.total,memory.used --format=csv,noheader";
        CommandExecutor.CommandResult result = CommandExecutor.executeCommand(command, TimeUnit.SECONDS.toMillis(5));
        if (!result.isSuccess()) {
            LOGGER.error("failed to execute gpu command={}||errorResult={}", command, result.getStdErrorOutput());
            return Lists.newArrayList();
        }
        return parseGpuInfo(result.getStdOutput()).stream().sorted(Comparator.comparingInt(GpuInfo::getIndex))
                .collect(Collectors.toList());
    }

    public List<GpuInfo> getGpuList() {
        return getGpuInfo();
    }

    public void syncGpuInfo() {
        List<GpuInfo> gpuInfoList = getGpuInfo();
        if (CollectionUtils.isEmpty(gpuInfoList)) {
            return;
        }
        long timeInMillis = System.currentTimeMillis();
        readWriteLock.writeLock().lock();
        try {
            if (cachedGpuHistoryInfoList.size() == MAX_CACHED_HISTORY_COUNT) {
                cachedGpuHistoryInfoList.poll();
            }
            Pair<Long, List<GpuInfo>> pair = new Pair<>(timeInMillis, gpuInfoList);
            cachedGpuHistoryInfoList.offer(pair);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * 解析nvidia-smi命令输出
     *
     * @param output nvidia-smi命令输出字符串
     * @return GPU信息列表
     */
    public static List<GpuInfo> parseGpuInfo(String output) {
        List<GpuInfo> gpuList = new ArrayList<>();
        // 正则表达式匹配GPU信息行
        Pattern pattern = Pattern.compile("^(\\d+),\\s*(.+?),\\s*(\\d+)\\s*MiB,\\s*(\\d+)\\s*MiB$");

        String[] lines = output.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                try {
                    int index = Integer.parseInt(matcher.group(1));
                    String name = matcher.group(2).trim();
                    int memoryTotal = Integer.parseInt(matcher.group(3));
                    int memoryUsed = Integer.parseInt(matcher.group(4));
                    gpuList.add(new GpuInfo(index, name, memoryTotal, memoryUsed));
                } catch (NumberFormatException e) {
                    LOGGER.error("无法解析行: {}", line, e);
                }
            } else {
                LOGGER.warn("格式不匹配: {}", line);
            }
        }
        return gpuList;
    }
}
