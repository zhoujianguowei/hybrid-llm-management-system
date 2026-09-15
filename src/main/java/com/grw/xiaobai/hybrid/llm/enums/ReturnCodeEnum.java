package com.grw.xiaobai.hybrid.llm.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public enum ReturnCodeEnum {
    SUCCESS_CODE(1001, "common.success", "OK"),
    ERROR_SYSTEM_ERROR(1002, "common.operation_failed", "System error"),
    BASE_LOGIC_EXCEPTION(7001, "common.business_failed", "Business logic error"),
    FRAMEWORK_VALIDATION_EXCEPTION(7002, "common.param_validate_failed", "Parameter validation failed"),
    OPEN_API_PERMISSION_FORBIDDEN(8001, "openapi.permission_forbidden", "Third-party API access forbidden");

    private int code;

    private String msgKey;

    private String desc;
}
