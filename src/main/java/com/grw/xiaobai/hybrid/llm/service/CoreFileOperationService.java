package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.entity.file.UploadTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface CoreFileOperationService {
    boolean isTerminated(String status);

    boolean checkDiskSpace(String path, long requiredSpace);

    boolean checkFileExists(String targetPath, String fileName);

    File getTempFile(UploadTask task);

    void writeChunkToFile(File tempFile, long offset, InputStream inputStream) throws IOException;

    void initTempFile(File tempFile, long totalSize) throws IOException;

    void deleteTempFile(String tempFilePath);

    ValidationResult validateUploadTask(UploadTask task, String username);

    class ValidationResult {
        private final UploadTask task;
        private final boolean isValid;
        private final String errorMessage;
        private final int errorCode;
        private final String msgKey;

        public ValidationResult(UploadTask task, boolean isValid, String errorMessage, int errorCode, String msgKey) {
            this.task = task;
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
            this.msgKey = msgKey;
        }

        public UploadTask getTask() {
            return task;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getErrorCode() {
            return errorCode;
        }

        public String getMsgKey() {
            return msgKey;
        }

        public static ValidationResult success(UploadTask task) {
            return new ValidationResult(task, true, null, 0, null);
        }

        public static ValidationResult fail(int errorCode, String errorMessage) {
            return new ValidationResult(null, false, errorMessage, errorCode, null);
        }

        public static ValidationResult fail(int errorCode, String msgKey, String errorMessage) {
            return new ValidationResult(null, false, errorMessage, errorCode, msgKey);
        }
    }
}
