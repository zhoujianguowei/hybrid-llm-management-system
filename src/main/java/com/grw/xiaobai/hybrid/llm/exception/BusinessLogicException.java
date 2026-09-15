package com.grw.xiaobai.hybrid.llm.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * 业务逻辑异常通用父类
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class BusinessLogicException extends BaseRuntimeException {
    protected Integer code;
    protected String msg;
    protected String msgKey;
    /**
     * 错误详情
     */
    protected String logicErrorDetail;

    @Override
    public String errorDetail() {
        return Optional.ofNullable(logicErrorDetail).orElse("business logic error");
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    @Override
    public String getMsgKey() {
        return msgKey;
    }
}
