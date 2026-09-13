package com.moyue.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：将业务异常与未捕获异常统一转换为 R&lt;T&gt;（HTTP 200 承载）。
 * 仅在 Spring MVC（servlet）环境下生效；网关为 WebFlux，由网关过滤器自行处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常 -> 对应业务码 */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败 -> 10001 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : ResultCode.PARAM_ERROR.getMessage();
        return R.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }

    /** 其余未捕获异常 -> 40001 内部异常（记录完整堆栈，便于排障） */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("[unhandled] 未捕获异常：", e);
        return R.fail(ResultCode.INTERNAL_ERROR.getCode(), ResultCode.INTERNAL_ERROR.getMessage());
    }
}
