package com.moyue.common.core.exception;

import com.moyue.common.core.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常收敛。
 *
 * <p>约定（架构说明书 11.3 / 10.5）：<ul>
 *   <li>业务错误一律以 <b>HTTP 200 + code != 0</b> 承载，不使用 HTTP 状态码表达业务失败；</li>
 *   <li>响应统一携带 {@code traceId}，便于全链路排查。</li>
 * </ul>
 *
 * @author moyue
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：@Valid / @Validated 标注的请求体 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + "：" + f.getDefaultMessage())
                .collect(Collectors.joining("；"));
        return R.fail(ErrorCode.PARAM_ERROR, msg.isEmpty() ? "参数校验失败" : msg);
    }

    /** 参数校验失败：表单绑定 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBind(BindException e) {
        FieldError first = e.getFieldError();
        String msg = first == null ? "参数绑定失败" : first.getField() + "：" + first.getDefaultMessage();
        return R.fail(ErrorCode.PARAM_ERROR, msg);
    }

    /** 参数校验失败：单参数约束 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        return R.fail(ErrorCode.PARAM_ERROR, msg);
    }

    /** 缺少必填参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(ErrorCode.PARAM_ERROR, "缺少必填参数：" + e.getParameterName());
    }

    /** 请求体不可解析 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return R.fail(ErrorCode.PARAM_ERROR, "请求体格式错误");
    }

    /** 请求方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(ErrorCode.PARAM_ERROR, "不支持的请求方法：" + e.getMethod());
    }

    /** 兜底：未知异常，返回 40001 并带上 traceId */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        String traceId = MDC.get(R.TRACE_ID);
        log.error("未捕获异常 uri={} traceId={}", request.getRequestURI(), traceId, e);
        String message = ErrorCode.INTERNAL_ERROR.getMessage()
                + (traceId == null ? "" : "（traceId=" + traceId + "）");
        return R.fail(ErrorCode.INTERNAL_ERROR.getCode(), message);
    }
}
