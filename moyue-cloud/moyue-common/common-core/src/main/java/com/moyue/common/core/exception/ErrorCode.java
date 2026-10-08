package com.moyue.common.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 业务错误码（5 位）。
 *
 * <p>取自架构说明书 4.3 错误码表全集，HTTP 统一返回 200 承载。
 *
 * @author moyue
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    /** 请求成功 */
    SUCCESS(0, "success"),
    /** 参数校验失败 */
    PARAM_ERROR(10001, "参数校验失败"),
    /** 未登录或 Token 失效 */
    UNAUTHORIZED(10002, "未登录或登录已失效"),
    /** 无权限访问该资源 */
    FORBIDDEN(10003, "无权限访问该资源"),
    /** 资源不存在或已下架 */
    NOT_FOUND(20001, "资源不存在或已下架"),
    /** 请求频率超限（Sentinel） */
    TOO_MANY_REQUESTS(30001, "请求过于频繁，请稍后再试"),
    /** 服务内部异常（含 traceId） */
    INTERNAL_ERROR(40001, "服务内部异常"),
    /** 服务间调用降级（Feign Fallback） */
    SERVICE_DEGRADED(40002, "依赖服务不可用，已降级处理"),
    /** 支付失败或订单超时关闭 */
    PAY_FAILED(50001, "支付失败或订单已超时关闭"),
    ;

    private final int code;
    private final String message;

    public static ErrorCode of(int code) {
        return Arrays.stream(values())
                .filter(e -> e.code == code)
                .findFirst()
                .orElse(null);
    }
}
