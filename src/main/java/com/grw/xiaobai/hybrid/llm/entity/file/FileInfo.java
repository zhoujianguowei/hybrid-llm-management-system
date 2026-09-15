package com.grw.xiaobai.hybrid.llm.entity.file;

import lombok.Data;

@Data
public class FileInfo {
    private String name;
    private String path;
    private Boolean isDirectory;
    private Long size;
    private String type;
    private String mimeType;
    private Long lastModified;
    private Integer filePermission;

    public static FileInfo fromFile(java.io.File file) {
        FileInfo info = new FileInfo();
        info.setName(file.getName());
        info.setPath(file.getAbsolutePath());
        info.setIsDirectory(file.isDirectory());
        info.setLastModified(file.lastModified());
        if (!file.isDirectory()) {
            info.setSize(file.length());
            info.setMimeType(getMimeType(file));
            info.setType(getFileType(info.getMimeType()));
        }
        return info;
    }

    private static String getMimeType(java.io.File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".bmp")) return "image/bmp";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".avi")) return "video/x-msvideo";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".flac")) return "audio/flac";
        if (name.endsWith(".aac")) return "audio/aac";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".txt") || name.endsWith(".bat") || name.endsWith(".sh")) return "text/plain";
        if (name.endsWith(".html")) return "text/html";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".js")) return "application/javascript";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".xml")) return "application/xml";
        if (name.endsWith(".zip")) return "application/zip";
        if (name.endsWith(".tar")) return "application/x-tar";
        if (name.endsWith(".gz")) return "application/gzip";
        if (name.endsWith(".7z")) return "application/x-7z-compressed";
        if (name.endsWith(".rar")) return "application/x-rar-compressed";
        return "application/octet-stream";
    }

    private static String getFileType(String mimeType) {
        if (mimeType.startsWith("image/")) return "image";
        if (mimeType.startsWith("video/")) return "video";
        if (mimeType.startsWith("audio/")) return "audio";
        if (mimeType.equals("application/pdf")) return "pdf";
        if (mimeType.startsWith("text/")) return "text";
        return "other";
    }
}
