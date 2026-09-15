package com.grw.xiaobai.hybrid.llm.utils;

import cn.hutool.core.io.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScriptResourceUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptResourceUtil.class);

    private ScriptResourceUtil() {
    }

    public static void extractScriptToDisk(String classpathResource, String targetPath) {
        try (InputStream in = ScriptResourceUtil.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Script resource not found in classpath: " + classpathResource);
            }
            File targetFile = new File(targetPath);
            File parentDir = targetFile.getParentFile();
            FileUtil.mkdir(parentDir);
            try (FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
            targetFile.setExecutable(true);
            LOGGER.info("脚本已提取: {} -> {}", classpathResource, targetPath);
        } catch (IOException e) {
            LOGGER.error("提取脚本失败: {}", classpathResource, e);
            throw new IllegalStateException("Failed to extract script: " + classpathResource, e);
        }
    }
}
