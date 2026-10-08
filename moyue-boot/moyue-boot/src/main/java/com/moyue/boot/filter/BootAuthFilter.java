package com.moyue.boot.filter;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.R;
import com.moyue.common.security.context.UserContext;
import com.moyue.common.security.model.LoginUser;
import com.moyue.common.security.util.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 单体形态的身份过滤器 —— <b>双项目适配点 ②（身份注入）</b>。
 *
 * <p>cloud 形态下这一步由网关 {@code JwtAuthGlobalFilter} 完成；单体没有网关，
 * 所以在进程内用 Servlet Filter 做等价的事：解析 JWT → 写 {@link UserContext}
 * → 用请求包装把身份头传给下游控制器，从而保证两种形态下业务代码拿身份的方式一致。
 *
 * <p>只在 {@code moyue.mode=boot} 时装配。
 *
 * @author moyue
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnProperty(prefix = "moyue", name = "mode", havingValue = "boot")
@RequiredArgsConstructor
public class BootAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;
    private final BootSecurityProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || isPermit(path)) {
            chain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (token == null) {
            write(response, ErrorCode.UNAUTHORIZED.getCode(), "缺少访问令牌");
            return;
        }
        LoginUser user;
        try {
            user = jwtUtils.parse(token);
        } catch (Exception e) {
            write(response, ErrorCode.UNAUTHORIZED.getCode(), "令牌无效或已过期");
            return;
        }
        if (user == null || user.getUserId() == null) {
            write(response, ErrorCode.UNAUTHORIZED.getCode(), "令牌无效或已过期");
            return;
        }
        if (path.startsWith(Constants.ADMIN_PATH_PREFIX)
                && !Integer.valueOf(Constants.USER_TYPE_OPERATOR).equals(user.getUserType())) {
            write(response, ErrorCode.FORBIDDEN.getCode(), "仅运营账号可访问后台接口");
            return;
        }

        UserContext.set(user);
        // 【启动修复】原实现只写 UserContext，从不建立 SecurityContext 的 Authentication——
        // 而 SecurityFilterChain 对 /api/v1/admin/** 要求 authenticated()，于是匿名身份被 403。
        // 这里把 LoginUser 包成 Authentication 写进 SecurityContextHolder，让 Security 放行，
        // 细粒度权限仍交给 AuthAspect 基于 UserContext 判定。
        List<SimpleGrantedAuthority> authorities = user.getRoleKeys().stream()
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, authorities));
        try {
            chain.doFilter(new UserHeaderRequestWrapper(request, user), response);
        } finally {
            // ThreadLocal 必须清理：容器线程复用会带脏身份到下一个请求
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isPermit(String path) {
        List<String> paths = properties.getPermitPaths();
        if (paths == null) {
            return false;
        }
        return paths.stream().anyMatch(p -> MATCHER.match(p, path));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            return header.substring(BEARER.length());
        }
        return request.getParameter("access_token");
    }

    private void write(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code == ErrorCode.UNAUTHORIZED.getCode() ? 401 : 403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(code, message)));
    }
}
