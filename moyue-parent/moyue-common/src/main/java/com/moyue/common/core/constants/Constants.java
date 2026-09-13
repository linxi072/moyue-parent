package com.moyue.common.core.constants;

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

    /** accessToken 类型 */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /** refreshToken 类型 */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private Constants() {
    }
}
