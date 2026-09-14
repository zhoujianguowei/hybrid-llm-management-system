package com.grw.xiaobai.hybrid.llm.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.URLUtil;
import com.grw.xiaobai.hybrid.llm.constant.CacheConstants;
import com.grw.xiaobai.hybrid.llm.service.FileManagerService;
import com.grw.xiaobai.hybrid.llm.service.SystemService;
import com.grw.xiaobai.hybrid.llm.entity.file.FileInfo;
import com.grw.xiaobai.hybrid.llm.manager.UserContextManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class FileManagerServiceImpl implements FileManagerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileManagerServiceImpl.class);

    @Resource
    private UserContextManager userContextManager;
    @Resource
    private SystemService systemService;

    @Cacheable(value = CacheConstants.MID_TIME_CACHE_NAME, key = "'rootDirectories'")
    public List<String> getRootDirectories() {
        if (systemService.isWindows()) {
            File[] roots = File.listRoots();
            List<String> result = new ArrayList<>();
            if (roots != null) {
                for (File root : roots) {
                    if (root.canRead()) {
                        result.add(root.getAbsolutePath());
                    }
                }
            }
            return result;
        } else {
            return Arrays.asList("/", System.getProperty("user.home"));
        }
    }

    public List<FileInfo> listFiles(String path) {
        File directory = new File(path);
        if (!directory.exists() || !directory.isDirectory()) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return new ArrayList<>();
        }

        List<FileInfo> result = new ArrayList<>();
        for (File file : files) {
            result.add(FileInfo.fromFile(file));
        }
        return result;
    }


    public List<FileInfo> listFilesAndSavePath(String path, boolean rememberPath) {
        List<FileInfo> files = listFiles(path);
        if (files != null && rememberPath) {
            userContextManager.setLastVisitedPath(path);
        }
        return files;
    }

    public List<FileInfo> listFilesAndSavePath(String path) {
        return listFilesAndSavePath(path, true);
    }

    public void clearLastVisitedFileDir() {
        userContextManager.clearLastVisitedFileDir();
    }


    public InputStream getFileInputStream(String path) throws Exception {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new Exception("File not found: " + path);
        }
        return new FileInputStream(file);
    }

    public String getMimeType(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        return FileInfo.fromFile(file).getMimeType();
    }

    public boolean fileExists(String path) {
        File file = new File(path);
        return file.exists();
    }

    public boolean isFile(String path) {
        File file = new File(path);
        return file.exists() && file.isFile();
    }

    public boolean isDirectory(String path) {
        File file = new File(path);
        return file.exists() && file.isDirectory();
    }

    public long getFileSize(String path) {
        File file = new File(path);
        return file.length();
    }

    public String getFileName(String path) {
        File file = new File(path);
        return file.getName();
    }

    public boolean isMediaFile(String mimeType) {
        return mimeType != null && (mimeType.startsWith("video/") || mimeType.startsWith("audio/"));
    }

    public PreviewInfo getPreviewInfo(String path, String rangeHeader) {
        if (!isFile(path)) {
            return new PreviewInfo(path, null, 0, false, false, null);
        }

        String mimeType = getMimeType(path);
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        long fileSize = getFileSize(path);
        boolean isMedia = isMediaFile(mimeType);
        RangeInfo rangeInfo = RangeInfo.parse(rangeHeader, fileSize);

        return new PreviewInfo(path, mimeType, fileSize, isMedia, true, rangeInfo);
    }

    public DownloadInfo getDownloadInfo(String path) {
        if (!fileExists(path)) {
            return new DownloadInfo(path, null, null, 0, false, false);
        }

        String fileName = getFileName(path);
        String mimeType = getMimeType(path);
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        long fileSize = getFileSize(path);
        boolean isDirectory = isDirectory(path);

        return new DownloadInfo(path, fileName, mimeType, fileSize, true, isDirectory);
    }

    public DownloadInfo getTaskDownloadInfo(String zipPath) {
        if (zipPath == null || !fileExists(zipPath)) {
            return null;
        }

        String fileName = getFileName(zipPath);
        long fileSize = getFileSize(zipPath);

        return new DownloadInfo(zipPath, fileName, "application/zip", fileSize, true, false);
    }


    public void writeFileToResponseWithRange(String path, PreviewInfo previewInfo, HttpServletResponse response) throws Exception {
        response.setContentType(previewInfo.getMimeType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");

        RangeInfo rangeInfo = previewInfo.getRangeInfo();
        if (!rangeInfo.isValid() || !previewInfo.isMedia()) {
            response.setContentLengthLong(previewInfo.getFileSize());
            writeFullFile(path, response);
            return;
        }

        writeRangeContent(path, previewInfo.getFileSize(), rangeInfo, response);
    }

    public void setupAndWriteDownloadResponse(String path, DownloadInfo downloadInfo, boolean isDirectory, HttpServletResponse response) throws Exception {
        response.setContentType(isDirectory ? "application/zip" : downloadInfo.getMimeType());

        if (isDirectory) {
            String rawName = downloadInfo.getFileName() + ".zip";
            String encodedName = URLUtil.encode(rawName, StandardCharsets.UTF_8);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + rawName + "\"; filename*=UTF-8''" + encodedName);
        } else {
            String rawName = downloadInfo.getFileName();
            String encodedName = URLUtil.encode(rawName, StandardCharsets.UTF_8);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + rawName + "\"; filename*=UTF-8''" + encodedName);
            response.setContentLengthLong(downloadInfo.getFileSize());
        }

        writeFullFile(path, response);
        response.flushBuffer();
    }

    public void setupAndWriteDownloadResponseWithRange(String path, DownloadInfo downloadInfo, String renameFileName,String rangeHeader, HttpServletResponse response) throws Exception {
        response.setContentType(downloadInfo.getMimeType());
        String rawName = Optional.ofNullable(renameFileName).orElse(downloadInfo.getFileName());
        String encodedName = URLUtil.encode(rawName, StandardCharsets.UTF_8);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + rawName + "\"; filename*=UTF-8''" + encodedName);
        response.setHeader("Accept-Ranges", "bytes");

        RangeInfo rangeInfo = RangeInfo.parse(rangeHeader, downloadInfo.getFileSize());

        if (rangeHeader != null && !rangeInfo.isValid()) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + downloadInfo.getFileSize());
            return;
        }

        if (rangeHeader != null) {
            writeRangeContent(path, downloadInfo.getFileSize(), rangeInfo, response);
        } else {
            response.setContentLengthLong(downloadInfo.getFileSize());
            writeFullFile(path, response);
        }
        response.flushBuffer();
    }

    private void writeRangeContent(String path, long fileSize, RangeInfo rangeInfo, HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + rangeInfo.getStart() + "-" + rangeInfo.getEnd() + "/" + fileSize);

        long length = rangeInfo.getContentLength();
        response.setContentLengthLong(length);

        try (RandomAccessFile raf = new RandomAccessFile(path, "r");
             OutputStream os = response.getOutputStream()) {
            raf.seek(rangeInfo.getStart());
            byte[] buffer = new byte[32768];
            long pos = 0;
            while (pos < length) {
                long remaining = length - pos;
                int bytesToRead = (remaining < buffer.length) ? (int) remaining : buffer.length;
                int read = raf.read(buffer, 0, bytesToRead);
                if (read == -1) break;
                try {
                    os.write(buffer, 0, read);
                } catch (IOException e) {
                    // 4. 核心优化：捕捉客户端主动断开连接（如划走视频、滑动进度条、Glide 取消请求）
                    String msg = e.getMessage();
                    if (e instanceof ClientAbortException ||
                            (msg != null && (msg.contains("Broken pipe") || msg.contains("Connection reset")))) {
                        LOGGER.info("Client closed connection prematurely during stream transmission for path: {}", path);
                        return; // 优雅退出，不向外层抛出堆栈报错
                    }
                    throw e; // 其他真正的网络或 I/O 异常继续向上抛出
                }
                pos += read;
            }
        }
    }

    private void writeFullFile(String path, HttpServletResponse response) throws Exception {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            if (!response.isCommitted()) {
                response.reset();
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
            return;
        }
        try (InputStream is = new FileInputStream(file)) {
            OutputStream os = response.getOutputStream();
            byte[] buffer = new byte[32768];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                try {
                    os.write(buffer, 0, bytesRead);
                } catch (IOException e) {
                    String msg = e.getMessage();
                    if (e instanceof ClientAbortException ||
                            (msg != null && (msg.contains("Broken pipe") || msg.contains("Connection reset")))) {
                        LOGGER.info("Client closed connection prematurely during download for path: {}", path);
                        return;
                    }
                    throw e;
                }
            }
        }
    }

    public boolean deleteFile(String path) {
        return FileUtil.del(path);
    }

    @Override
    public boolean createDirectory(String path) {
        File dir = new File(path);
        if (dir.exists()) {
            return false;
        }
        FileUtil.mkdir(dir);
        return dir.exists();
    }
}
