package com.moyue.common.config;

import com.moyue.common.interceptor.AdminRoleInterceptor;
import com.moyue.common.interceptor.HeaderInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 跨域与鉴权装配（作用于各业务服务）。
 * 网关侧跨域由 gateway 的 globalcors 统一处理。
 * 两层防线：
 * 1. {@link HeaderInterceptor} —— 把网关注入的 X-User-Id / X-User-Role 装配进 SecurityContextHolder（全路径）；
 * 2. {@link AdminRoleInterceptor} —— /api/v1/admin/** 路径级默认拒绝（role=3），
 *    细粒度控制用 @RequiresRoles / @RequiresPermissions 注解（PreAuthorizeAspect）。
 * 仅当应用为 Servlet（MVC）环境时才加载，避免污染 WebFlux 网关。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AdminRoleInterceptor adminRoleInterceptor;

    @Autowired
    private HeaderInterceptor headerInterceptor;

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
        // 后台接口（/api/v1/admin/**）仅允许 role=3 管理员访问，关闭 16-6 越权风险
        registry.addInterceptor(adminRoleInterceptor)
                .addPathPatterns("/api/v1/admin/**");
    }
}
