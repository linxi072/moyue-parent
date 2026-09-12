package com.moyue.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.UUID;

/**
 * 统一响应体 R&lt;T&gt;。
 * 字段严格对齐架构设计稿：code / message / data / traceId。
 * HTTP 统一返回 200 承载，业务结果通过 code 表达（0 成功，其余为业务错误码）。
 *
 * @param <T> 业务数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码：0 成功，其余见 ResultCode */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 全链路追踪 ID */
    private String traceId;

    public R() {
    }

    public R(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    /** 成功（携带数据） */
    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, newTraceId());
    }

    /** 成功（无数据） */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /** 失败（使用 ResultCode 默认文案） */
    public static <T> R<T> fail(ResultCode code) {
        return new R<>(code.getCode(), code.getMessage(), null, newTraceId());
    }

    /** 失败（自定义文案） */
    public static <T> R<T> fail(ResultCode code, String message) {
        return new R<>(code.getCode(), message, null, newTraceId());
    }

    /** 失败（自定义业务码与文案） */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, newTraceId());
    }

    /** 生成简易 traceId，生产环境建议接入 SkyWalking / MDC */
    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
