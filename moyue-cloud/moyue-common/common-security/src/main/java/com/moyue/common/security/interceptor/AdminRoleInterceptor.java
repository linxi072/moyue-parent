package com.moyue.common.security.interceptor;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.security.aspect.AuthAspect;
import com.moyue.common.security.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台路径守卫拦截器。
 *
 * <p>所有 {@value Constants#ADMIN_PATH_PREFIX}/** 接口强制要求 {@code userType = 3}（运营），
 * 防止读者/作家 token 误入后台。开关：{@code moyue.security.admin-path-guard}。
 *
 * <p><b>【架构缺口 G-4 补充】</b>架构说明书仅要求「后台角色与 user_type 两层解耦」，
 * 未定义后台路径的强制校验点，本拦截器即为该校验点，已登记至《架构缺口补齐说明.md》。
 *
 * @author moyue
 */
public class AdminRoleInterceptor implements HandlerInterceptor {

    private final boolean enabled;

    public AdminRoleInterceptor(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith(Constants.ADMIN_PATH_PREFIX)) {
            AuthAspect.requireOperator(UserContext.get());
        }
        return true;
    }
}
