package com.grw.xiaobai.hybrid.llm.service;

import com.grw.xiaobai.hybrid.llm.entity.file.FileInfo;

import java.io.InputStream;
import java.util.List;
import javax.servlet.http.HttpServletResponse;

public interface FileManagerService {
    List<String> getRootDirectories();

    List<FileInfo> listFiles(String path);

    List<FileInfo> listFilesAndSavePath(String path, boolean rememberPath);

    List<FileInfo> listFilesAndSavePath(String path);

    void clearLastVisitedFileDir();

    InputStream getFileInputStream(String path) throws Exception;

    String getMimeType(String path);

    boolean fileExists(String path);

    boolean isFile(String path);

    boolean isDirectory(String path);

    long getFileSize(String path);

    String getFileName(String path);

    boolean isMediaFile(String mimeType);

    PreviewInfo getPreviewInfo(String path, String rangeHeader);

    DownloadInfo getDownloadInfo(String path);

    DownloadInfo getTaskDownloadInfo(String zipPath);

    void writeFileToResponseWithRange(String path, PreviewInfo previewInfo, HttpServletResponse response) throws Exception;

    void setupAndWriteDownloadResponse(String path, DownloadInfo downloadInfo, boolean isDirectory, HttpServletResponse response) throws Exception;

    void setupAndWriteDownloadResponseWithRange(String path, DownloadInfo downloadInfo,String renameFileName, String rangeHeader, HttpServletResponse response) throws Exception;

    boolean deleteFile(String path);

    boolean createDirectory(String path);

    class RangeInfo {
        private final long start;
        private final long end;
        private final long contentLength;
        private final long fileSize;
        private final boolean isValid;

        public RangeInfo(long start, long end, long contentLength, long fileSize, boolean isValid) {
            this.start = start;
            this.end = end;
            this.contentLength = contentLength;
            this.fileSize = fileSize;
            this.isValid = isValid;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        public long getContentLength() {
            return contentLength;
        }

        public long getFileSize() {
            return fileSize;
        }

        public boolean isValid() {
            return isValid;
        }

        public static RangeInfo parse(String rangeHeader, long fileSize) {
            if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
                return new RangeInfo(0, fileSize - 1, fileSize, fileSize, true);
            }

            try {
                String[] ranges = rangeHeader.replace("bytes=", "").split("-");
                long start = 0;
                long end = fileSize - 1;

                if (!ranges[0].isEmpty()) {
                    start = Long.parseLong(ranges[0]);
                }
                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    end = Long.parseLong(ranges[1]);
                }

                if (start >= fileSize) {
                    return new RangeInfo(0, 0, 0, fileSize, false);
                }

                long contentLength = end - start + 1;
                return new RangeInfo(start, end, contentLength, fileSize, true);
            } catch (NumberFormatException e) {
                return new RangeInfo(0, 0, 0, fileSize, false);
            }
        }
    }

    class PreviewInfo {
        private final String path;
        private final String mimeType;
        private final long fileSize;
        private final boolean isMedia;
        private final boolean exists;
        private final RangeInfo rangeInfo;

        public PreviewInfo(String path, String mimeType, long fileSize, boolean isMedia, boolean exists, RangeInfo rangeInfo) {
            this.path = path;
            this.mimeType = mimeType;
            this.fileSize = fileSize;
            this.isMedia = isMedia;
            this.exists = exists;
            this.rangeInfo = rangeInfo;
        }

        public String getPath() {
            return path;
        }

        public String getMimeType() {
            return mimeType;
        }

        public long getFileSize() {
            return fileSize;
        }

        public boolean isMedia() {
            return isMedia;
        }

        public boolean isExists() {
            return exists;
        }

        public RangeInfo getRangeInfo() {
            return rangeInfo;
        }
    }

    class DownloadInfo {
        private final String path;
        private final String fileName;
        private final String mimeType;
        private final long fileSize;
        private final boolean exists;
        private final boolean isDirectory;

        public DownloadInfo(String path, String fileName, String mimeType, long fileSize, boolean exists, boolean isDirectory) {
            this.path = path;
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.fileSize = fileSize;
            this.exists = exists;
            this.isDirectory = isDirectory;
        }

        public String getPath() {
            return path;
        }

        public String getFileName() {
            return fileName;
        }

        public String getMimeType() {
            return mimeType;
        }

        public long getFileSize() {
            return fileSize;
        }

        public boolean isExists() {
            return exists;
        }

        public boolean isDirectory() {
            return isDirectory;
        }
    }
}
