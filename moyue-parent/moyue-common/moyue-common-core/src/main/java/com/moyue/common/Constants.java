package com.moyue.common;

/**
 * 全局常量：鉴权头、用户上下文透传头、JWT 类型标识。
 */
public final class Constants {

    /** 鉴权请求头 */
    public static final String AUTH_HEADER = "Authorization";

    /** Bearer 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** 网关向下游服务透传的用户 ID 头 */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** 网关向下游服务透传的用户角色头 */
    public static final String USER_ROLE_HEADER = "X-User-Role";

    /** 服务间内部调用鉴权头（X-Service-Token），仅 /api/v1/internal/** 校验 */
    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    /** 网关来源标识头（X-Forwarded-By），由网关注入以标识请求来源 */
    public static final String FORWARDED_BY_HEADER = "X-Forwarded-By";

    /** 网关来源标识期望值（moyue-gateway），与 X-Forwarded-By 比对 */
    public static final String FORWARDED_BY_GATEWAY = "moyue-gateway";

    /** 网关信任头签名（X-Gateway-Sig，HMAC-SHA256），由网关注入、业务侧（P1 后）验真 */
    public static final String GATEWAY_SIG_HEADER = "X-Gateway-Sig";

    /** accessToken 类型 */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /** refreshToken 类型 */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private Constants() {
    }
}
