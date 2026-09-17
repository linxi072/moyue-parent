package com.moyue.common;

import com.moyue.common.security.GatewaySigInterceptor;
import com.moyue.common.security.HeaderInterceptor;
import com.moyue.common.security.InternalAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 跨域与鉴权装配（作用于各业务服务）。
 * 网关侧跨域由 gateway 的 globalcors 统一处理。
 * 三层防线：
 * 1. {@link HeaderInterceptor} —— 把网关注入的 X-User-Id / X-User-Role 装配进 SecurityContextHolder（全路径）；
 * 2. {@link AdminRoleInterceptor} —— /api/v1/admin/** 路径级默认拒绝（role=3），细粒度用 @RequiresRoles / @RequiresPermissions；
 * 3. P2-I {@link InternalAuthInterceptor} —— /api/v1/internal/** 校验 X-Service-Token，杜绝伪造内部端点调用；
 *    P2-I(P1) {@link GatewaySigInterceptor} —— 默认关闭，enforceSourceCheck=true 时校验网关来源/签名。
 * 仅当应用为 Servlet（MVC）环境时才加载，避免污染 WebFlux 网关。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AdminRoleInterceptor adminRoleInterceptor;

    @Autowired
    private HeaderInterceptor headerInterceptor;

    @Autowired
    private InternalAuthInterceptor internalAuthInterceptor;

    @Autowired(required = false)
    private GatewaySigInterceptor gatewaySigInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 身份上下文装配（全路径，供注解切面与业务代码取用当前用户）
        registry.addInterceptor(headerInterceptor).addPathPatterns("/**");
        // 后台接口（/api/v1/admin/**）仅允许 role=3 管理员访问，关闭越权风险
        registry.addInterceptor(adminRoleInterceptor)
                .addPathPatterns("/api/v1/admin/**");
        // P2-I：内部端点令牌校验（/api/v1/internal/**）
        registry.addInterceptor(internalAuthInterceptor)
                .addPathPatterns("/api/v1/internal/**");
        // P2-I(P1)：网关来源/签名校验（默认关闭，enforceSourceCheck=true 时启用；内部端点已由令牌管，排除）
        if (gatewaySigInterceptor != null) {
            registry.addInterceptor(gatewaySigInterceptor)
                    .addPathPatterns("/**")
                    .excludePathPatterns("/api/v1/internal/**");
        }
    }
}
