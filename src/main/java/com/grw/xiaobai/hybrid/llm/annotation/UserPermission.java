package com.grw.xiaobai.hybrid.llm.annotation;

import com.grw.xiaobai.hybrid.llm.enums.UserRoleEnum;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UserPermission {
    UserRoleEnum[] value() default {};
}