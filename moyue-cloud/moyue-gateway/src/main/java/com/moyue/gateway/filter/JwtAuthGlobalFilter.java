package com.moyue.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.common.security.model.LoginUser;
import com.moyue.common.security.util.JwtUtils;
import com.moyue.gateway.config.WhitelistProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关身份过滤：解析 JWT → 注入身份头 → 后台路径的运营主体校验。
 *
 * <p><b>职责边界</b>（架构说明书 5.x）：<ul>
 *   <li>网关只做「身份识别与传递」，<strong>不做细粒度鉴权</strong>；菜单级权限由各服务
 *       的 {@code @RequiresPermissions} 切面判定，与 boot 单体模式行为一致；</li>
 *   <li>注入 {@code X-User-Id / X-User-Name / X-User-Type / X-User-Role}，下游服务
 *       从请求头取身份，保证 cloud 与 boot 两种模式下业务代码零差异。</li>
 * </ul>
 *
 * <p><b>刻意不做的事</b>：不在此处校验 refresh token（那是 /auth/refresh 的职责），
 * 也不在此处查库（网关无数据源，避免成为瓶颈）。
 *
 * @author moyue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER = "Bearer ";
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final JwtUtils jwtUtils;
    private final WhitelistProperties whitelistProperties;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 预检请求直接放行：CORS 由 CorsConfig 统一处理
        if (HttpMethod.OPTIONS.matches(request.getMethod().name())) {
            return chain.filter(exchange);
        }
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange, "缺少访问令牌");
        }
        LoginUser user;
        try {
            user = jwtUtils.parse(token);
        } catch (Exception e) {
            return unauthorized(exchange, "令牌无效或已过期");
        }
        if (user == null || user.getUserId() == null) {
            return unauthorized(exchange, "令牌无效或已过期");
        }

        // 后台路径：必须是运营主体（架构说明书 G-4 裁定，与 AdminRoleInterceptor 口径一致）
        if (isAdminPath(path) && !Integer.valueOf(Constants.USER_TYPE_OPERATOR).equals(user.getUserType())) {
            return forbidden(exchange, "仅运营账号可访问后台接口");
        }

        ServerHttpRequest mutated = request.mutate()
                .header(Constants.HEADER_USER_ID, String.valueOf(user.getUserId()))
                .header(Constants.HEADER_USER_NAME, nvl(user.getUsername()))
                .header(Constants.HEADER_USER_TYPE, String.valueOf(nvl(user.getUserType())))
                .header(Constants.HEADER_USER_ROLE, String.join(",", user.getRoleKeys()))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        // 早于 NettyRoutingFilter，晚于 RouteToRequestUrlFilter
        return -100;
    }

    private boolean isWhitelisted(String path) {
        if (whitelistProperties.getWhitelist() == null) {
            return false;
        }
        return whitelistProperties.getWhitelist().stream().anyMatch(p -> MATCHER.match(p, path));
    }

    private boolean isAdminPath(String path) {
        if (whitelistProperties.getAdminPrefixes() == null) {
            return false;
        }
        return whitelistProperties.getAdminPrefixes().stream()
                .anyMatch(p -> MATCHER.match(p, path));
    }

    private String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER)) {
            return header.substring(BEARER.length());
        }
        // 兼容从查询参数传 token 的场景（如文件下载链接）
        return request.getQueryParams().getFirst("access_token");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return write(exchange, HttpStatus.UNAUTHORIZED, R.fail(
                com.moyue.common.core.exception.ErrorCode.UNAUTHORIZED.getCode(), message));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return write(exchange, HttpStatus.FORBIDDEN, R.fail(
                com.moyue.common.core.exception.ErrorCode.FORBIDDEN.getCode(), message));
    }

    /** 网关层统一以 JSON 返回，且沿用 HTTP 语义状态码（与服务内 HTTP 200 承载业务码不同） */
    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, R<?> body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            byte[] bytes = "{\"code\":40001,\"message\":\"网关响应序列化失败\"}".getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }

    private static String nvl(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
