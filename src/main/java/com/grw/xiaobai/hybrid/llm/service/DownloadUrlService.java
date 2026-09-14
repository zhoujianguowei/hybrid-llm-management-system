package com.grw.xiaobai.hybrid.llm.service;

public interface DownloadUrlService {
    String generateDownloadUrl(String taskId, String filePath, String sessionId);

    DownloadUrlInfo getDownloadInfo(String urlId);

    boolean isValidUrl(String urlId);

    void cleanup();

    class DownloadUrlInfo {
        private String urlId;
        private String taskId;
        private String filePath;
        private String sessionId;
        private Long createdTime;

        public String getUrlId() {
            return urlId;
        }

        public void setUrlId(String urlId) {
            this.urlId = urlId;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public Long getCreatedTime() {
            return createdTime;
        }

        public void setCreatedTime(Long createdTime) {
            this.createdTime = createdTime;
        }
    }
}
