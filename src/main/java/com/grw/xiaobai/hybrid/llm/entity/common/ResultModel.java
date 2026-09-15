package com.grw.xiaobai.hybrid.llm.entity.common;


import java.util.Map;
import lombok.Data;

/**
 *
 */
@Data
public class ResultModel<T> {
    private Boolean success;
    private Integer code;
    private String msg;
    private String msgKey;
    private Map<String, Object> params;
    private T data;

    public static <T> ResultModel<T> OK(T content) {
        ResultModel<T> ret = new ResultModel();
        ret.success = true;
        ret.code = 0;
        ret.msg = "OK";
        ret.msgKey = "ok";
        ret.data = content;
        return ret;
    }

    public static ResultModel<Void> OK() {
        return ResultModel.OK(null);
    }

    public static <T> ResultModel<T> OK(String msgKey, T content) {
        ResultModel<T> ret = new ResultModel();
        ret.success = true;
        ret.code = 0;
        ret.msg = "OK";
        ret.msgKey = msgKey;
        ret.data = content;
        return ret;
    }

    public static <T> ResultModel<T> fail(String message) {
        return fail(null, null, message, null);
    }

    public static <T> ResultModel<T> fail(String msgKey, String message) {
        return fail(null, msgKey, null, message, null);
    }

    public static <T> ResultModel<T> fail(String msgKey, Map<String, Object> params, String message) {
        return fail(null, msgKey, params, message, null);
    }

    public static <T> ResultModel<T> fail(Integer errorCode, String message) {
        return fail(errorCode, null, null, message, null);
    }

    public static <T> ResultModel<T> fail(Integer errorCode, String msgKey, String message) {
        return fail(errorCode, msgKey, null, message, null);
    }

    public static <T> ResultModel<T> fail(Integer errorCode, String msgKey, Map<String, Object> params, String message) {
        return fail(errorCode, msgKey, params, message, null);
    }

    public static <T> ResultModel<T> fail(Integer errorCode, String message, T data) {
        return fail(errorCode, null, null, message, data);
    }

    public static <T> ResultModel<T> fail(Integer errorCode, String msgKey, String message, T data) {
        return fail(errorCode, msgKey, null, message, data);
    }

    public static <T> ResultModel<T> fail(Integer errorCode, String msgKey, Map<String, Object> params, String message, T data) {
        ResultModel<T> ret = new ResultModel<>();
        ret.success = false;
        ret.code = errorCode;
        ret.msg = message;
        ret.msgKey = msgKey;
        ret.params = params;
        ret.data = data;
        return ret;
    }

}

