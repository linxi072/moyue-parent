package com.moyue.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全放行配置。
 *
 * <p><b>【架构缺口 G-4 裁定】</b>架构说明书未给出 Spring Security 配置方式，本实现裁定为
 * 无状态 SecurityFilterChain（SessionCreationPolicy.STATELESS + 关闭 CSRF），
 * 白名单路径由 {@code moyue.security.permit-paths} 外置，避免硬编码在通用模块中。
 *
 * @author moyue
 */
@Data
@ConfigurationProperties(prefix = "moyue.security")
public class SecurityProperties {

    /** 是否启用后台路径强制校验（/api/v1/admin/** 必须 userType = 3） */
    private boolean adminPathGuard = true;

    /** 放行路径（Ant 风格） */
    private List<String> permitPaths = new ArrayList<>(List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/captcha/**",
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/doc.html",
            "/webjars/**",
            "/favicon.ico",
            "/error"
    ));
}
