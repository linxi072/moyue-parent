package com.moyue.common;

/**
 * 业务错误码枚举，严格对齐架构设计稿。
 * 0 成功 / 10001 参数 / 10002 未登录 / 10003 无权限 / 20001 资源不存在 / 40001 内部异常。
 */
public enum ResultCode {

    /** 请求成功 */
    SUCCESS(0, "success"),

    /** 参数校验失败 */
    PARAM_ERROR(10001, "参数校验失败"),

    /** 未登录 / 令牌已失效 */
    UNAUTHORIZED(10002, "未登录或令牌已失效"),

    /** 登录已过期（与 UNAUTHORIZED 同码，文案更明确） */
    TOKEN_EXPIRED(10002, "登录已过期，请重新登录"),

    /** 令牌非法 / 无权限（10003） */
    TOKEN_INVALID(10003, "令牌非法"),

    /** 无权限访问该资源 */
    FORBIDDEN(10003, "无权限访问该资源"),

    /** 资源不存在或已下架 */
    RESOURCE_NOT_FOUND(20001, "资源不存在或已下架"),

    /** 请求频率超限（Sentinel 限流） */
    FREQUENCY_LIMIT(30001, "请求频率超限"),

    /** 服务内部异常 */
    INTERNAL_ERROR(40001, "服务内部异常"),

    /** 支付失败 / 订单超时关闭 */
    PAYMENT_FAILED(50001, "支付失败或订单超时关闭");

    private final int code;

    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
