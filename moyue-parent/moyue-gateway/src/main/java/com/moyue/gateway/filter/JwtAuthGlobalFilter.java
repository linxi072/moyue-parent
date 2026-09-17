package com.moyue.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.JwtProvider;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.SecuritySignUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关全局鉴权过滤器。
 * 职责：
 * 1. 白名单（登录 / 注册 / 刷新）直接放行；
 * 2. 其余请求校验 Bearer token，过期返回 10002、非法返回 10003（HTTP 200 承载 R&lt;T&gt;）；
 * 3. 校验通过后将 userId / role 注入下游请求头 X-User-Id / X-User-Role。
 */
@Component
@Order(-1)
public class JwtAuthGlobalFilter implements GlobalFilter {

    /**
     * 免鉴权白名单（精确匹配，逗号分隔配置项，对齐 RuoYi gateway 白名单配置化）。
     * 默认：登录 / 注册 / 刷新 / 系统后台登录。
     */
    @Value("${moyue.auth.whitelist:/api/v1/auth/login,/api/v1/auth/register,/api/v1/auth/refresh,/api/v1/system/login}")
    private List<String> whiteList;

    /**
     * 免鉴权前缀：上传文件（封面 / 头像等）为公开静态资源，
     * 图片标签无法携带 Authorization 头，故按前缀整体放行（只读，不含写接口）。
     */
    @Value("${moyue.auth.whitelist-prefixes:/api/v1/files/}")
    private List<String> whitePrefixes;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    /** P2-I(P1)：网关 HMAC 签名密钥；留空则不签名（配置驱动优雅降级，dev 本地不签名） */
    @Value("${moyue.security.gatewaySigSecret:}")
    private String gatewaySigSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单（精确路径或公开静态资源前缀）直接放行
        if (whiteList.contains(path) || isWhitePrefix(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(Constants.AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(Constants.BEARER_PREFIX)) {
            return writeUnauthorized(exchange, ResultCode.UNAUTHORIZED);
        }

        String token = authHeader.substring(Constants.BEARER_PREFIX.length());
        try {
            JwtProvider.JwtClaims claims = jwtProvider.parse(token);
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header(Constants.USER_ID_HEADER, String.valueOf(claims.getUserId()))
                    .header(Constants.USER_ROLE_HEADER, String.valueOf(claims.getRole()))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (BizException e) {
            // 过期 -> 10002；非法 -> 10003
            ResultCode code = (e.getCode() == ResultCode.TOKEN_EXPIRED.getCode())
                    ? ResultCode.TOKEN_EXPIRED
                    : ResultCode.TOKEN_INVALID;
            return writeUnauthorized(exchange, code);
        }
    }

    /** 命中公开静态资源前缀则免鉴权 */
    private boolean isWhitePrefix(String path) {
        for (String prefix : whitePrefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** P2-I：向请求注入网关注入来源标识（X-Forwarded-By）与可选 HMAC 签名（X-Gateway-Sig / X-Gateway-Ts） */
    private ServerHttpRequest withGatewayHeaders(ServerHttpRequest req) {
        ServerHttpRequest.Builder builder = req.mutate()
                .header(Constants.FORWARDED_BY_HEADER, Constants.FORWARDED_BY_GATEWAY);
        if (gatewaySigSecret != null && !gatewaySigSecret.isBlank()) {
            String ts = String.valueOf(System.currentTimeMillis());
            String sig = SecuritySignUtil.sign(req.getMethod().name(), req.getURI().getPath(), ts, gatewaySigSecret);
            builder.header("X-Gateway-Ts", ts).header(Constants.GATEWAY_SIG_HEADER, sig);
        }
        return builder.build();
    }

    /** 写入统一响应体 R&lt;T&gt;，HTTP 状态固定 200 */
    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, ResultCode code) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        R<Object> body = R.fail(code.getCode(), code.getMessage());
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            // 兜底：序列化失败时手写最小 JSON
            String fallback = "{\"code\":" + code.getCode() + ",\"message\":\"" + code.getMessage() + "\"}";
            byte[] bytes = fallback.getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        }
    }
}
