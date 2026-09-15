package com.grw.xiaobai.hybrid.llm.enums;

import com.grw.xiaobai.hybrid.llm.constant.FilePermissionMask;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FilePermissionEnum {
    READ(FilePermissionMask.READ_MASK),
    DOWNLOAD(FilePermissionMask.DOWNLOAD_MASK),
    DELETE(FilePermissionMask.DELETE_MASK),
    EXECUTE(FilePermissionMask.EXECUTE_MASK),
    UPLOAD(FilePermissionMask.UPLOAD_MASK);

    private final int mask;
}
