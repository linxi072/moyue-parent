package com.moyue.common.security.filter;

import com.moyue.common.security.config.JwtProperties;
import com.moyue.common.security.context.UserContext;
import com.moyue.common.security.model.LoginUser;
import com.moyue.common.security.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析 Authorization 头，建立 {@link UserContext}。
 *
 * <p><b>设计取舍</b>：本过滤器只做「解析 + 写上下文」，不做「拦截」。
 * 是否放行交给 Spring Security 的 SecurityFilterChain（Cloud 版走网关、Boot 版走本地配置），
 * 越权判定交给 {@code AuthAspect}。这样通用模块不需要感知各服务的白名单差异。
 *
 * @author moyue
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final JwtProperties properties;

    private final AntPathMatcher matcher = new AntPathMatcher();

    /** 需要跳过的路径，由 SecurityAutoConfiguration 注入 */
    private List<String> permitPaths = List.of();

    public void setPermitPaths(List<String> permitPaths) {
        this.permitPaths = permitPaths == null ? List.of() : permitPaths;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String uri = request.getRequestURI();
            if (!isPermit(uri)) {
                String token = jwtUtils.resolveToken(request.getHeader(properties.getHeader()));
                if (token != null && !token.isBlank()) {
                    LoginUser user = jwtUtils.parse(token);
                    if (user != null) {
                        UserContext.set(user);
                        request.setAttribute("moyue.userId", user.getUserId());
                        // 【启动修复】同 BootAuthFilter：必须建立 SecurityContext 的 Authentication，
                        // 否则 SecurityFilterChain 对 /api/v1/admin/** 的 authenticated() 会拒绝匿名身份。
                        List<SimpleGrantedAuthority> authorities = user.getRoleKeys().stream()
                                .map(SimpleGrantedAuthority::new).toList();
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(user, null, authorities));
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isPermit(String uri) {
        return permitPaths.stream().anyMatch(p -> matcher.match(p, uri));
    }
}
