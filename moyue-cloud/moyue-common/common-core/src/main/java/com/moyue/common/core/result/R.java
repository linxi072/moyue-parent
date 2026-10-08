package com.moyue.common.core.result;

import com.moyue.common.core.exception.ErrorCode;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一响应体 R&lt;T&gt;。
 *
 * <p>约定（架构说明书 4.3 / 11.3）：<ul>
 *   <li>所有接口统一返回 <b>HTTP 200</b>，业务状态由响应体 {@code code} 承载；</li>
 *   <li>禁止控制器直接返回裸对象；</li>
 *   <li>{@code traceId} 全链路贯穿，由 MDC 注入。</li>
 * </ul>
 *
 * <p>【待确认 G-5】架构文档仅锁定 {@code R.fail(40002)} 一种构造，成功侧方法名由本工程裁定为 {@code ok}。
 *
 * @author moyue
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 链路追踪 ID 在 MDC 中的键 */
    public static final String TRACE_ID = "traceId";

    /** 业务状态码，0 为成功，其余为 5 位业务错误码 */
    private int code;

    /** 提示文案 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 链路追踪 ID */
    private String traceId;

    public R() {
    }

    public R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get(TRACE_ID);
    }

    // ---------------- 成功 ----------------

    public static <T> R<T> ok() {
        return new R<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> R<T> ok(T data, String message) {
        return new R<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    // ---------------- 失败 ----------------

    public static <T> R<T> fail(int code) {
        ErrorCode ec = ErrorCode.of(code);
        return new R<>(code, ec == null ? "业务处理失败" : ec.getMessage(), null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return new R<>(errorCode.getCode(), message, null);
    }

    // ---------------- 判定 ----------------

    /** 是否成功（code == 0） */
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
