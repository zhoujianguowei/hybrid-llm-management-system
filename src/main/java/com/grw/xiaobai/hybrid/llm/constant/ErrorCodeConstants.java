package com.grw.xiaobai.hybrid.llm.constant;

import com.grw.xiaobai.hybrid.llm.annotation.UserPermission;
import com.grw.xiaobai.hybrid.llm.enums.ReturnCodeEnum;

/**
 * 通用错误码常量定义。
 *
 * <p>与 HTTP 状态码对齐，用于 ResultModel 的 code 字段。
 * 业务异常码请参见 {@link ReturnCodeEnum}。
 */
public class ErrorCodeConstants {

    // ==================== HTTP 状态码（ResultModel 直接使用） ====================

    /**
     * 请求成功。
     * 仅在 LoginService 中首次登录管理员需强制改密码时使用此非零成功码。
     */
    public static final int SUCCESS = 200;

    /** 管理员首次登录，需要强制修改密码后才能继续操作。 */
    public static final int FORCE_CHANGE_PASSWORD = 201;

    /**
     * 未授权／身份验证失败。
     * 适用场景：session 过期或无效、用户名或密码错误。
     */
    public static final int UNAUTHORIZED = 401;

    /**
     * 账户被封禁。
     * 当前用户的 accountStatus 为 block，无法执行任何操作。
     */
    public static final int USER_BLOCKED = 402;

    /**
     * 权限不足。
     * 当前用户角色不满足接口要求的 {@link UserPermission} 权限。
     */
    public static final int FORBIDDEN = 403;

    /** 请求的资源（文件、目录等）不存在。 */
    public static final int NOT_FOUND = 404;

    /** 请求参数缺失或格式错误。 */
    public static final int BAD_REQUEST = 400;

    /**
     * 磁盘空间不足，无法完成上传或写入操作。
     * 仅在文件上传场景中使用。
     */
    public static final int INSUFFICIENT_STORAGE = 507;

    /**
     * 请求过多（并发上传任务数超过限制）。
     */
    public static final int TOO_MANY_REQUESTS = 429;

    // ==================== 业务异常码（对应 ReturnCodeEnum） ====================

    /** 通用操作成功。对应 {@link ReturnCodeEnum#SUCCESS_CODE} */
    public static final int SUCCESS_CODE = 1001;

    /** 系统内部异常。对应 {@link ReturnCodeEnum#ERROR_SYSTEM_ERROR} */
    public static final int ERROR_SYSTEM_ERROR = 1002;

    /**
     * 通用业务逻辑异常。
     * 对应 {@link ReturnCodeEnum#BASE_LOGIC_EXCEPTION}
     * 使用场景：参数校验失败、业务规则冲突、资源状态异常等通用业务错误。
     */
    public static final int BASE_LOGIC_EXCEPTION = 7001;

    /**
     * 框架参数校验异常。
     * 对应 {@link ReturnCodeEnum#FRAMEWORK_VALIDATION_EXCEPTION}
     * 由 Spring 参数校验（@Valid、@Validated）触发时使用。
     */
    public static final int FRAMEWORK_VALIDATION_EXCEPTION = 7002;

    /**
     * 三方 API 没有访问权限。
     * 对应 {@link ReturnCodeEnum#OPEN_API_PERMISSION_FORBIDDEN}
     * 调用外部开放平台接口时鉴权失败。
     */
    public static final int OPEN_API_PERMISSION_FORBIDDEN = 8001;
}
