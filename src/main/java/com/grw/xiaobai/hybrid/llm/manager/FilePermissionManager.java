package com.grw.xiaobai.hybrid.llm.manager;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.entity.file.FilePermissionTree;
import com.grw.xiaobai.hybrid.llm.service.SystemService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FilePermissionManager {
    private static final String PERMISSION_FILE_PATH =
            FilePathConstants.FILE_MANAGER_DIR + File.separator + "file_permission.json";

    @Getter
    private volatile FilePermissionTree tree;
    private static final Logger LOGGER = LoggerFactory.getLogger(FilePermissionManager.class);

    @Resource
    private SystemService systemService;

    @PostConstruct
    public void init() {
        tree = loadFromFile();
        if (tree == null) {
            tree = createDefaultTree();
            saveToFile();
        } else {
            pruneMissingPaths();
            syncMissingRoots();
        }
    }

    public int pruneMissingPaths() {
        try {
            int removed = tree.pruneMissingPaths(path -> new File(path).exists());
            if (removed > 0) {
                LOGGER.info("Pruned {} file permission entries whose paths no longer exist", removed);
                saveToFile();
            }
            return removed;
        } catch (Exception e) {
            LOGGER.error("Failed to prune missing file permission entries", e);
            return 0;
        }
    }

    private void syncMissingRoots() {
        if (!systemService.isWindows()) {
            return;
        }
        File[] roots = File.listRoots();
        if (roots == null) {
            return;
        }
        boolean changed = false;
        for (File root : roots) {
            if (!root.canRead()) {
                continue;
            }
            String rootPath = FilePermissionTree.normalizePath(root.getAbsolutePath());
            if (tree.addRootPath(rootPath)) {
                LOGGER.info("Detected new disk, adding to permission tree: {}", rootPath);
                changed = true;
            }
        }
        if (changed) {
            saveToFile();
        }
    }

    private FilePermissionTree loadFromFile() {
        File file = new File(PERMISSION_FILE_PATH);
        if (file.exists() && file.isFile()) {
            try {
                String json = FileUtil.readString(file, StandardCharsets.UTF_8);
                return JSON.parseObject(json, FilePermissionTree.class);
            } catch (Exception e) {
                LOGGER.error("Failed to load file permission config", e);
            }
        }
        return null;
    }

    public void saveToFile() {
        try {
            synchronized (this) {
                FileUtil.mkdir(new File(PERMISSION_FILE_PATH).getParentFile());
                String json = JSON.toJSONString(tree, SerializerFeature.PrettyFormat);
                FileUtil.writeString(json, new File(PERMISSION_FILE_PATH), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save file permission config", e);
        }
    }

    private FilePermissionTree createDefaultTree() {
        FilePermissionTree tree = new FilePermissionTree();
        tree.setLevel(0);

        if (systemService.isWindows()) {
            File[] roots = File.listRoots();
            java.util.List<String> rootPaths = new java.util.ArrayList<>();
            if (roots != null) {
                for (File root : roots) {
                    if (root.canRead()) {
                        rootPaths.add(root.getAbsolutePath());
                    }
                }
            }
            tree.initDefault(rootPaths);
        } else {
            tree.initDefault(Lists.newArrayList("/"));
        }
        return tree;
    }

 }
