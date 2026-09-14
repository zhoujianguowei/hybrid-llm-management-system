package com.grw.xiaobai.hybrid.llm.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户性别枚举类
 */
@Getter
@AllArgsConstructor
public enum SexEnum {
    MAN(1, "男"),
    WOMEN(2, "女"),
    UNKNOWN(3, "未知");
    private Integer code;
    private String msg;
}
