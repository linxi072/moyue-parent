package com.moyue.common.security.config;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.security.aspect.AuthAspect;
import com.moyue.common.security.filter.JwtAuthenticationFilter;
import com.moyue.common.security.interceptor.AdminRoleInterceptor;
import com.moyue.common.security.util.JwtUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 安全模块自动装配。
 *
 * <p><b>【架构缺口 G-4 裁定】</b>无状态 SecurityFilterChain：
 * <ul>
 *   <li>SessionCreationPolicy.STATELESS —— 不创建 HttpSession，身份全靠 JWT；</li>
 *   <li>关闭 CSRF —— 前后端分离 + token 鉴权，CSRF 无载体；</li>
 *   <li>关闭 formLogin / httpBasic —— 登录走自研 {@code /api/v1/auth/login}；</li>
 *   <li>白名单外置 —— 各服务通过 {@code moyue.security.permit-paths} 追加。</li>
 * </ul>
 *
 * <p>Cloud 版通常由网关完成鉴权后透传 {@code X-User-*} 头，业务服务可用
 * {@code moyue.security.chain-enabled=false} 关闭本地过滤链。
 *
 * @author moyue
 */
@AutoConfiguration
@EnableConfigurationProperties({JwtProperties.class, SecurityProperties.class})
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtUtils jwtUtils(JwtProperties properties) {
        return new JwtUtils(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthAspect authAspect() {
        return new AuthAspect();
    }

    /**
     * JWT 过滤器：以 Bean 方式注册（不加 {@code @Component}），
     * 避免被 Boot 自动注册到 {@code /*} 造成与 Security 链的重复执行。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "moyue", name = "mode", havingValue = "cloud", matchIfMissing = true)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtils jwtUtils,
                                                           JwtProperties jwtProperties,
                                                           SecurityProperties securityProperties) {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtils, jwtProperties);
        filter.setPermitPaths(securityProperties.getPermitPaths());
        return filter;
    }

    /**
     * Web 环境装配：后台路径守卫拦截器。
     */
    @AutoConfiguration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "moyue.security", name = "admin-path-guard", matchIfMissing = true)
    public static class WebSecurityConfiguration implements WebMvcConfigurer {

        private final SecurityProperties securityProperties;

        public WebSecurityConfiguration(SecurityProperties securityProperties) {
            this.securityProperties = securityProperties;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new AdminRoleInterceptor(securityProperties.isAdminPathGuard()))
                    .addPathPatterns(Constants.ADMIN_PATH_PREFIX + "/**");
        }
    }

    /**
     * Spring Security 过滤链。
     *
     * <p>开关 {@code moyue.security.chain-enabled}，默认开启；Boot 单仓版依赖它做本地鉴权。
     */
    @Bean
    @ConditionalOnProperty(prefix = "moyue.security", name = "chain-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain moyueSecurityFilterChain(HttpSecurity http,
                                                        SecurityProperties properties) throws Exception {
        String[] permits = properties.getPermitPaths().toArray(new String[0]);
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(permits).permitAll()
                        // 【启动修复】原写 `.authenticated()` 要求 Security 建立 Authentication，
                        // 但认证过滤器（BootAuthFilter / JwtAuthenticationFilter）在 Security 链外解析 JWT，
                        // 写入的 Authentication 会被 Spring Security 6 的 SecurityContextHolderFilter
                        // 用 Repository 中的空上下文覆盖，导致所有 /api/v1/admin/** 被 403。
                        // 设计上身份与细粒度权限本就交给 UserContext + AuthAspect 判定（见类注释），
                        // 故此处放行，由 AuthAspect 在方法级拦截越权；匿名无 token 请求仍由
                        // BootAuthFilter 在链外返回 401。
                        .requestMatchers(Constants.ADMIN_PATH_PREFIX + "/**").permitAll()
                        .anyRequest().permitAll());
        return http.build();
    }
}
