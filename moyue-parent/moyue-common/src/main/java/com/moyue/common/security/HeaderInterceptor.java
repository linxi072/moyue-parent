package com.moyue.common.security;

import com.moyue.common.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 身份头拦截器：把网关注入的 X-User-Id / X-User-Role 装配进 {@link SecurityContextHolder}，
 * 供 @RequiresRoles / @RequiresPermissions 注解切面与业务代码取用（对齐 RuoYi HeaderInterceptor）。
 */
@Component
public class HeaderInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String uid = request.getHeader(Constants.USER_ID_HEADER);
        String role = request.getHeader(Constants.USER_ROLE_HEADER);
        if (uid != null && !uid.isBlank()) {
            try {
                SecurityContextHolder.set(new SecurityContextHolder.LoginUser(
                        Long.parseLong(uid), role == null ? null : Integer.parseInt(role)));
            } catch (NumberFormatException ignored) {
                // 头被篡改为非数字：视为匿名，由注解/拦截器按 403 处理
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        SecurityContextHolder.clear();
    }
}
