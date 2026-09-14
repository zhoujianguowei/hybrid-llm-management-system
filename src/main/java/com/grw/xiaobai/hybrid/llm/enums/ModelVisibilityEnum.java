package com.grw.xiaobai.hybrid.llm.enums;

import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ModelVisibilityEnum {
    PRIVATE(0, "私有"),
    PROTECTED(1, "受保护"),
    PUBLIC(2, "公开");

    private final int level;
    private final String desc;

    /**
     * Check if the given user role can access this visibility level.
     * PRIVATE: admin only
     * PROTECTED: admin and user
     * PUBLIC: all roles
     */
    public boolean canAccess(UserRoleEnum userRole) {
        if (this == PUBLIC) return true;
        if (this == PROTECTED) return userRole == UserRoleEnum.admin || userRole == UserRoleEnum.user;
        if (this == PRIVATE) return userRole == UserRoleEnum.admin;
        return false;
    }

    public static ModelVisibilityEnum fromLevel(int level) {
        return Arrays.stream(values())
                .filter(v -> v.level == level)
                .findFirst()
                .orElse(PUBLIC);
    }

    public static ModelVisibilityEnum fromName(String name) {
        for (ModelVisibilityEnum v : values()) {
            if (v.name().equals(name)) return v;
        }
        return PUBLIC;
    }
}
