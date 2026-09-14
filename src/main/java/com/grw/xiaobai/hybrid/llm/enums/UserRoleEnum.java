package com.grw.xiaobai.hybrid.llm.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserRoleEnum {
    admin("admin", "管理员"),
    user("user", "普通用户"),
    guest("guest", "访客");

    private final String code;
    private final String desc;
}