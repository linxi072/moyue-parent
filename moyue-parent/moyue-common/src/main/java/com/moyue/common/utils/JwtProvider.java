package com.moyue.common.utils;

import com.moyue.common.core.constants.Constants;
import com.moyue.common.core.domain.ResultCode;
import com.moyue.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（基于 jjwt 0.12.x）。
 * 提供 accessToken / refreshToken 签发与解析校验。
 * 密钥通过 {@code moyue.jwt.secret} 注入，缺省提供一个 32 字节以上的开发密钥。
 */
@Component
public class JwtProvider {

    /**
     * 默认开发密钥。
     * 仅用于本地开发；生产必须通过环境变量 MOYUE_JWT_SECRET 注入，长度不短于 32 字节。
     */
    private static final String DEFAULT_SECRET = "moyue-jwt-dev-secret-key-0123456789abcdefghij";

    @Value("${moyue.jwt.secret:" + DEFAULT_SECRET + "}")
    private String secret;

    private SecretKey getSecretKey() {
        // HS256 要求密钥长度不小于 256 位（32 字节）
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 accessToken */
    public String createAccessToken(Long userId, Integer role, long ttlMillis) {
        return buildToken(userId, role, Constants.TOKEN_TYPE_ACCESS, ttlMillis);
    }

    /** 签发 refreshToken（TTL 较长，如 7 天） */
    public String createRefreshToken(Long userId, long ttlMillis) {
        return buildToken(userId, null, Constants.TOKEN_TYPE_REFRESH, ttlMillis);
    }

    private String buildToken(Long userId, Integer role, String type, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(getSecretKey())
                .compact();
    }

    /** 解析并校验 token；过期抛 TOKEN_EXPIRED，非法抛 TOKEN_INVALID */
    public JwtClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            JwtClaims jwtClaims = new JwtClaims();
            jwtClaims.setUserId(((Number) claims.get("userId")).longValue());
            Object roleObj = claims.get("role");
            jwtClaims.setRole(roleObj == null ? null : ((Number) roleObj).intValue());
            jwtClaims.setType((String) claims.get("type"));
            return jwtClaims;
        } catch (ExpiredJwtException e) {
            throw new BizException(ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
    }

    /** 校验 token 是否有效（不抛异常） */
    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    /** JWT 解析后的声明 */
    public static class JwtClaims {

        private Long userId;

        private Integer role;

        private String type;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Integer getRole() {
            return role;
        }

        public void setRole(Integer role) {
            this.role = role;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
