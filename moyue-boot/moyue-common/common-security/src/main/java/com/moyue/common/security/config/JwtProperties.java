package com.moyue.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性。
 *
 * <p><b>【架构缺口 G-3 裁定】</b>架构说明书未定义 JWT claim 字段名与签名算法，本实现按
 * RuoYi 惯例 + Spring Boot 3 实践裁定如下，已在《架构缺口补齐说明.md》登记：
 * <pre>
 *   claim       : userId / username / userType / roles / tokenType
 *   tokenType   : access（短时效）| refresh（长时效，仅用于换取 access）
 *   签名算法     : HS256（对称，密钥外置）
 *   密钥配置     : moyue.jwt.secret（生产环境由环境变量 MOYUE_JWT_SECRET 覆盖）
 *   请求头       : Authorization: Bearer &lt;token&gt;
 * </pre>
 *
 * @author moyue
 */
@Data
@ConfigurationProperties(prefix = "moyue.jwt")
public class JwtProperties {

    /** 是否启用 JWT 鉴权（Boot 单仓版可关闭走 Session） */
    private boolean enabled = true;

    /** HMAC 密钥，HS256 要求不少于 32 字节 */
    private String secret = "moyue-novel-default-jwt-secret-please-change-in-prod";

    /** access token 有效期（分钟），默认 2 小时 */
    private long accessExpireMinutes = 120L;

    /** refresh token 有效期（天），默认 7 天 */
    private long refreshExpireDays = 7L;

    /** 请求头名称 */
    private String header = "Authorization";

    /** token 前缀 */
    private String prefix = "Bearer ";
}
