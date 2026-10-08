package com.moyue.common.security.util;

import com.moyue.common.security.config.JwtProperties;
import com.moyue.common.security.model.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT 签发与校验。
 *
 * <p>claim 约定见 {@link JwtProperties} 类注释（G-3 裁定）。
 * access token 用于业务请求，refresh token 仅用于换取新的 access token，
 * 二者通过 {@code tokenType} claim 区分，避免 refresh token 被用于访问业务接口。
 *
 * @author moyue
 */
@Slf4j
@RequiredArgsConstructor
public class JwtUtils {

    /** access token 类型标识 */
    public static final String TOKEN_TYPE_ACCESS = "access";
    /** refresh token 类型标识 */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USER_TYPE = "userType";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_SUPER_ADMIN = "superAdmin";

    private final JwtProperties properties;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("moyue.jwt.secret 长度不足 32 字节，HS256 无法使用");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * 签发 access token。
     *
     * @param user 登录主体
     * @return token 字符串
     */
    public String createAccessToken(LoginUser user) {
        return build(user, TOKEN_TYPE_ACCESS,
                System.currentTimeMillis() + properties.getAccessExpireMinutes() * 60_000L);
    }

    /**
     * 签发 refresh token。
     *
     * @param user 登录主体
     * @return token 字符串
     */
    public String createRefreshToken(LoginUser user) {
        return build(user, TOKEN_TYPE_REFRESH,
                System.currentTimeMillis() + properties.getRefreshExpireDays() * 86_400_000L);
    }

    private String build(LoginUser user, String tokenType, long expireMillis) {
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_USER_TYPE, user.getUserType())
                .claim(CLAIM_ROLES, user.getRoleKeys())
                .claim(CLAIM_SUPER_ADMIN, Boolean.TRUE.equals(user.getSuperAdmin()))
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(new Date())
                .expiration(new Date(expireMillis))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 校验并解析 token，失败返回 null（不抛异常，交由上层决定是否放行）。
     *
     * @param token 原始 token（不含 Bearer 前缀）
     * @return 登录主体，失败返回 null
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String type = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (!TOKEN_TYPE_ACCESS.equals(type)) {
                log.warn("非 access token 被用于业务访问，tokenType={}", type);
                return null;
            }
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get(CLAIM_ROLES, List.class);
            return LoginUser.builder()
                    .userId(claims.get(CLAIM_USER_ID, Long.class))
                    .username(claims.getSubject())
                    .userType(claims.get(CLAIM_USER_TYPE, Integer.class))
                    .roleKeys(roles == null ? Set.of() : roles.stream()
                            .filter(r -> r != null && !r.isBlank())
                            .collect(Collectors.toSet()))
                    // 【启动修复】原实现漏写 / 漏读 superAdmin claim，导致 LoginUser.superAdmin 恒为 null，
                    // 于是 hasPermission / hasRole 永远进不了「超管恒为 true」分支，超管访问带
                    // @RequiresPermissions 的端点被 AuthAspect 判 FORBIDDEN。这里把超管标识随 token 往返。
                    .superAdmin(claims.get(CLAIM_SUPER_ADMIN, Boolean.class))
                    .build();
        } catch (ExpiredJwtException e) {
            log.debug("token 已过期：{}", e.getMessage());
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("token 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从带前缀的 Authorization 头中剥离 token。
     *
     * @param header 请求头原值
     * @return 纯 token，无前缀或空返回 null
     */
    public String resolveToken(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String prefix = properties.getPrefix();
        if (prefix != null && !prefix.isBlank() && header.startsWith(prefix)) {
            return header.substring(prefix.length()).trim();
        }
        return header.trim();
    }

    /**
     * access token 有效期（秒），供登录接口回传给前端。
     *
     * @return 秒数
     */
    public long accessExpireSeconds() {
        return properties.getAccessExpireMinutes() * 60L;
    }
}
