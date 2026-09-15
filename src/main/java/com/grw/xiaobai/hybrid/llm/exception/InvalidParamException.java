package com.grw.xiaobai.hybrid.llm.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * 参数校验异常
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class InvalidParamException extends BaseRuntimeException {
    protected Integer code;
    protected String msg;
    protected String msgKey;
    /**
     * 错误详情
     */
    protected String paramErrorDetail;

    @Override
    public String errorDetail() {
        return Optional.ofNullable(paramErrorDetail).orElse("invalid business param");
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
