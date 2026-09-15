package com.grw.xiaobai.hybrid.llm.annotation;

import com.grw.xiaobai.hybrid.llm.constant.FilePathConstants;
import com.grw.xiaobai.hybrid.llm.enums.FilePermissionEnum;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FilePermission {
    FilePermissionEnum value();

    String pathParam() default FilePathConstants.PATH_PARAM;

    String rejectKey() default FilePathConstants.PERMISSION_DENIED_KEY;
}
