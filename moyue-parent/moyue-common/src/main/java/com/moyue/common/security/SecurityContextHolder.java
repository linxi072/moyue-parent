package com.moyue.common.security;

/**
 * 请求级安全上下文（ThreadLocal，对齐 RuoYi SecurityContextHolder 模式）。
 *
 * <p>由 {@link HeaderInterceptor} 从网关注入的 X-User-Id / X-User-Role 头装配；
 * 注解切面 {@link PreAuthorizeAspect} 读取；请求结束由拦截器 afterCompletion 清理，防串号。</p>
 */
public final class SecurityContextHolder {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private SecurityContextHolder() {
    }

    /** 当前登录用户（网关鉴权后的身份投影） */
    public static final class LoginUser {
        private final Long userId;
        private final Integer role;

        public LoginUser(Long userId, Integer role) {
            this.userId = userId;
            this.role = role;
        }

        public Long getUserId() {
            return userId;
        }

        public Integer getRole() {
            return role;
        }
    }

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    /** @return 当前登录用户；网关白名单放行的匿名请求返回 null */
    public static LoginUser get() {
        return CONTEXT.get();
    }

    /** @return 当前用户角色；匿名返回 null */
    public static Integer currentRole() {
        LoginUser u = CONTEXT.get();
        return u == null ? null : u.getRole();
    }

    /** @return 当前用户 ID；匿名返回 null */
    public static Long currentUserId() {
        LoginUser u = CONTEXT.get();
        return u == null ? null : u.getUserId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
