package com.grw.xiaobai.hybrid.llm.handler;

import com.google.common.collect.Lists;
import com.grw.xiaobai.hybrid.llm.entity.common.ResultModel;
import com.grw.xiaobai.hybrid.llm.enums.ReturnCodeEnum;
import com.grw.xiaobai.hybrid.llm.exception.BaseRuntimeException;
import java.util.List;
import java.util.Optional;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * controller全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException ex) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Client closed connection during transfer: {}", ex.getMessage());
        }
    }

    /**
     * 处理{@link BaseRuntimeException}类型的异常
     *
     * @param baseRuntimeException
     * @return
     */
    @ExceptionHandler(BaseRuntimeException.class)
    public ResultModel<String> handleCommonException(BaseRuntimeException baseRuntimeException) {
        LOGGER.error("common business logic exception occurred||errorDetail={}", Optional.ofNullable(baseRuntimeException.getMsg()).orElse(baseRuntimeException.errorDetail()), baseRuntimeException);
        Integer code = Optional.ofNullable(baseRuntimeException.getCode()).orElse(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getCode());
        String msg = Optional.ofNullable(baseRuntimeException.getMsg()).orElse(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getDesc());
        String msgKey = Optional.ofNullable(baseRuntimeException.getMsgKey()).orElse(ReturnCodeEnum.BASE_LOGIC_EXCEPTION.getMsgKey());
        return ResultModel.fail(code, msgKey, msg, baseRuntimeException.errorDetail());
    }

    /**
     * bean参数属性校验出错
     *
     * @param paramException
     * @return
     */
    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class})
    public ResultModel<List<String>> bindingException(Exception paramException) {
        LOGGER.error("system param validate exception", paramException);
        List<String> errorInfoList = Lists.newArrayList();
        List<FieldError> fieldErrorList = Lists.newArrayList();
        if (paramException instanceof BindException) {
            fieldErrorList = ((BindException) paramException).getBindingResult().getFieldErrors();
        } else if (paramException instanceof MethodArgumentNotValidException) {
            fieldErrorList = ((MethodArgumentNotValidException) paramException).getBindingResult().getFieldErrors();
        }
        fieldErrorList.forEach(fieldError -> errorInfoList.add("Field: " + fieldError.getField() + "||Detail: " + fieldError.getDefaultMessage()));
        return ResultModel.fail(ReturnCodeEnum.FRAMEWORK_VALIDATION_EXCEPTION.getCode(), ReturnCodeEnum.FRAMEWORK_VALIDATION_EXCEPTION.getMsgKey(), "Parameter validation failed", errorInfoList);
    }

    /**
     * 其它类型的系统异常
     *
     * @param throwable
     * @return
     */
    @ExceptionHandler(Throwable.class)
    public ResultModel<String> commonException(Throwable throwable) {
        LOGGER.error("system exception", throwable);
        String errorMsg = throwable.getClass().getSimpleName() + "|" + throwable.getMessage();
        return ResultModel.fail(ReturnCodeEnum.ERROR_SYSTEM_ERROR.getCode(), ReturnCodeEnum.ERROR_SYSTEM_ERROR.getMsgKey(), ReturnCodeEnum.ERROR_SYSTEM_ERROR.getDesc(), errorMsg);
    }

    @Component
    public static class GlobalUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

        private static final Logger logger = LoggerFactory.getLogger(GlobalUncaughtExceptionHandler.class);

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            // 打印线程信息和完整的堆栈
            logger.error("捕获到来自线程 {} (ID: {}) 的未处理异常:", t.getName(), t.getId(), e);
            // e 对象本身就会打印完整的堆栈信息
        }
    }
}
