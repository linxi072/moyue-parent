package com.moyue.common.security.context;

import com.moyue.common.security.model.LoginUser;

import java.util.Optional;

/**
 * 用户上下文（ThreadLocal）。
 *
 * <p>由 {@code JwtAuthenticationFilter} 在请求入口写入，由 {@code AuthAspect}、
 * {@code MetaObjectFillHandler}、各 Service 读取；请求结束后必须 clear，
 * 否则线程池复用会造成身份串号。
 *
 * @author moyue
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Optional<LoginUser> optional() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * 当前用户 ID，未登录返回 null。
     *
     * @return 用户 ID
     */
    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    /**
     * 当前用户 ID，未登录时返回兜底值。
     *
     * @param defaultValue 兜底值
     * @return 用户 ID
     */
    public static Long getUserIdOrDefault(Long defaultValue) {
        Long id = getUserId();
        return id == null ? defaultValue : id;
    }

    public static String getUsername() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUsername();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
