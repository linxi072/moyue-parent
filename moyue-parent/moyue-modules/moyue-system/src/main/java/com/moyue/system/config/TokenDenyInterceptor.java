package com.moyue.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 令牌黑名单拦截器：强退（在线用户强制下线）后，原令牌进入 Redis 黑名单
 * {@code moyue:auth:deny:{md5(token)}}（TTL = 令牌剩余有效期），
 * 命中则直接 401 拒绝，保证强退即时生效。
 *
 * <p>Redis 异常只 warn 不阻断（黑名单为辅助防线，TTL 到期后令牌自然失效）。
 * 拦截路径由 {@link SystemWebMvcConfig} 统一注册到 /api/v1/admin/**。</p>
 */
@Component
public class TokenDenyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TokenDenyInterceptor.class);

    /** 黑名单 key 前缀 */
    public static final String DENY_KEY_PREFIX = "moyue:auth:deny:";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenDenyInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // CORS 预检放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (token == null) {
            return true;
        }
        try {
            Boolean denied = redisTemplate.hasKey(DENY_KEY_PREFIX + md5(token));
            if (Boolean.TRUE.equals(denied)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(R.fail(ResultCode.UNAUTHORIZED)));
                return false;
            }
        } catch (Exception e) {
            // Redis 不可用：黑名单校验降级跳过，只告警不阻断
            log.warn("[token-deny] 黑名单校验失败，降级放行：{}", e.getMessage());
        }
        return true;
    }

    /** 从 Authorization 头解析 Bearer 令牌 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(Constants.AUTH_HEADER);
        if (header == null || !header.startsWith(Constants.BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(Constants.BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** MD5 摘要（32 位小写十六进制） */
    public static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 MD5，理论上不可达
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }
}
