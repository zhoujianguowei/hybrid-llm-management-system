package com.grw.xiaobai.hybrid.llm.constant;

public class FilePermissionMask {
    private static final int READ_BYTE_LENGTH = 0;
    private static final int EXECUTE_BYTE_LENGTH = 2;
    private static final int DOWNLOAD_BYTE_LENGTH = 4;
    private static final int UPLOAD_BYTE_LENGTH = 6;
    private static final int DELETE_BYTE_LENGTH = 8;
    private static final int FOLDER_TYPE_BYTE_LENGTH = 14;
    private static final int PROTECTED_TYPE_BYTE_LENGTH = 10;
    public static final int READ_MASK = 1 << READ_BYTE_LENGTH;
    public static final int EXECUTE_MASK = 1 << EXECUTE_BYTE_LENGTH;
    public static final int DOWNLOAD_MASK = 1 << DOWNLOAD_BYTE_LENGTH;
    public static final int UPLOAD_MASK = 1 << UPLOAD_BYTE_LENGTH;
    public static final int DELETE_MASK = 1 << DELETE_BYTE_LENGTH;
    public static final int PROTECTED_MASK = 1 << PROTECTED_TYPE_BYTE_LENGTH;
}
