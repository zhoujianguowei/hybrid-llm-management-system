package com.grw.xiaobai.hybrid.llm.service.impl;

import com.grw.xiaobai.hybrid.llm.constant.ErrorCodeConstants;
import com.grw.xiaobai.hybrid.llm.constant.FileTaskConstants;
import com.grw.xiaobai.hybrid.llm.service.CoreFileOperationService;
import com.grw.xiaobai.hybrid.llm.entity.file.UploadTask;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CoreFileOperationServiceImpl implements CoreFileOperationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreFileOperationServiceImpl.class);

    public boolean isTerminated(String status) {
        return FileTaskConstants.STATUS_COMPLETED.equals(status)
                || FileTaskConstants.STATUS_CANCELLED.equals(status)
                || FileTaskConstants.STATUS_FAILED.equals(status);
    }

    public boolean checkDiskSpace(String path, long requiredSpace) {
        try {
            File targetDir = new File(path);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            return targetDir.getFreeSpace() >= requiredSpace;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean checkFileExists(String targetPath, String fileName) {
        File targetFile = new File(targetPath, fileName);
        return targetFile.exists();
    }

    public File getTempFile(UploadTask task) {
        if (task != null && task.getTempFilePath() != null) {
            return new File(task.getTempFilePath());
        }
        return null;
    }

    public void writeChunkToFile(File tempFile, long offset, InputStream inputStream) throws IOException {
        LOGGER.info("writeChunkToFile - file: {}, offset: {}", tempFile.getAbsolutePath(), offset);
        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
            if (raf.length() < offset) {
                raf.setLength(offset);
            }
            raf.seek(offset);
            long copied = IOUtils.copy(inputStream, java.nio.channels.Channels.newOutputStream(raf.getChannel()));
            LOGGER.info("writeChunkToFile - copied: {} bytes, file length after write: {}", copied, raf.length());
        }
    }

    public void initTempFile(File tempFile, long totalSize) throws IOException {
        if (tempFile.getParentFile() != null) {
            tempFile.getParentFile().mkdirs();
        }
        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
            raf.setLength(totalSize);
        }
    }

    public void deleteTempFile(String tempFilePath) {
        if (tempFilePath != null) {
            File tempFile = new File(tempFilePath);
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public ValidationResult validateUploadTask(UploadTask task, String username) {
        if (task == null) {
            return ValidationResult.fail(ErrorCodeConstants.NOT_FOUND, "upload.task_not_found", "Upload task not found");
        }
        if (!username.equals(task.getUsername())) {
            return ValidationResult.fail(ErrorCodeConstants.FORBIDDEN, "upload.user_mismatch", "User mismatch");
        }
        return ValidationResult.success(task);
    }

}
