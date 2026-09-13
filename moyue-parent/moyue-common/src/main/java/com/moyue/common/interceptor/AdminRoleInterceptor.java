package com.moyue.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.config.WebMvcConfig;
import com.moyue.common.core.constants.Constants;
import com.moyue.common.core.domain.R;
import com.moyue.common.core.domain.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 后台接口角色拦截器。
 * <p>仅允许 {@code role=3}（管理员）访问 {@code /api/v1/admin/**}。
 * 网关已在鉴权通过后注入 {@link Constants#USER_ROLE_HEADER}，本拦截器做服务端二次断言，
 * 避免普通用户 / 作者仅凭有效令牌越权调用后台接口（技术债 16-6）。</p>
 * <p>非管理员（缺头、非数字、或角色不等于 3）返回 {@link ResultCode#FORBIDDEN}
 * （10003，HTTP 200 承载 {@code R<T>}），与全局统一响应体保持一致。
 * 拦截路径由 {@link WebMvcConfig} 统一注册，仅对所有 servlet 业务服务生效。</p>
 */
@Component
public class AdminRoleInterceptor implements HandlerInterceptor {

    /** 管理员角色值，对齐 user.role 枚举：1 读者 / 2 作者 / 3 管理员 */
    private static final int ADMIN_ROLE = 3;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        // CORS 预检直接放行，角色校验交由真实请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        int role = parseRole(request.getHeader(Constants.USER_ROLE_HEADER));
        if (role != ADMIN_ROLE) {
            writeForbidden(response);
            return false;
        }
        return true;
    }

    private int parseRole(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(roleHeader.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(ResultCode.FORBIDDEN)));
    }
}
